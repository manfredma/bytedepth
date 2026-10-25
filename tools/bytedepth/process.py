"""共用子进程、输出策略及文件操作，不编排部署。"""

from contextlib import ExitStack
import hashlib
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile

# TD-0007：既有 Jest/jsdom 工具依赖通知，不属于运行时告警。
NPM_DEPRECATIONS = {
    "npm warn deprecated inflight@1.0.6: This module is not supported, and leaks memory. Do not use it. Check out lru-cache if you want a good and tested way to coalesce async requests by a key value, which is much more comprehensive and powerful.",
    "npm warn deprecated whatwg-encoding@3.1.1: Use @exodus/bytes instead for a more spec-conformant and faster implementation",
    "npm warn deprecated glob@7.2.3: Glob versions prior to v9 are no longer supported",
}

class ToolError(Exception):
    def __init__(self, message, status=1):
        super().__init__(message)
        self.status = status if 0 < status < 126 else 1


def warning_lines(text):
    """TD-0006 允许列表，由本地命令与正式 Host 日志共用。"""
    blocked = []
    for number, line in enumerate(text.splitlines(), 1):
        if line in NPM_DEPRECATIONS:
            continue
        lower = line.lower()
        if not re.search(r"(^|[^a-z])warn(?:ing)?([^a-z]|$)", lower):
            continue
        javassist = re.search(r"^\[warning\].*org\.javassist:javassist:jar:3\.21\.0-ga(?:\s|$)", lower)
        random = re.search(r"warn [0-9]+ --- .*o\.a\.c\.util\.sessionidgeneratorbase\s*: creation of securerandom instance for session id generation using \[sha1prng\] took \[([1-9][0-9][0-9])\] milliseconds\.$", lower)
        if not javassist and not (random and random[1] != "100"):
            blocked.append(f"{number}:{line}")
    return blocked


def capture(args, cwd, env=None):
    result = subprocess.run(args, cwd=cwd, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise ToolError(f"{args[0]} failed: {result.stderr.strip()}", result.returncode)
    if result.stderr:
        print(result.stderr, end="", file=sys.stderr)
        if warning_lines(result.stderr):
            raise ToolError("Unallowlisted WARN or WARNING in captured command stderr")
    return result.stdout


def redact(text, secrets):
    for secret in secrets:
        if secret:
            text = text.replace(secret, "[REDACTED]")
    return text


def stop_child(child):
    # 自有进程组可能在会话首进程退出后继续运行；必须始终发送信号，
    # 即使 poll()/wait() 已报告首进程退出。
    try:
        os.killpg(child.pid, signal.SIGTERM)
    except ProcessLookupError:
        child.wait()
        return
    try:
        child.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass
    finally:
        try:
            os.killpg(child.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        child.wait()


def run(args, cwd, env=None, log=None, secrets=(), warnings=False):
    """流式读取子进程；退出或日志错误立即失败，不执行 Shell 字符串。"""
    if isinstance(args, (str, bytes)):
        raise TypeError("Command arguments must be an array")
    blocked = []
    with ExitStack() as stack:
        output = stack.enter_context(Path(log).open("w")) if log is not None else None
        child = subprocess.Popen(args, cwd=cwd, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
        stack.callback(child.stdout.close)
        try:
            for line in child.stdout:
                if warnings:
                    blocked.extend(warning_lines(line))
                safe = redact(line, secrets)
                print(safe, end="", flush=True)
                if output:
                    output.write(safe)
                    output.flush()
            status = child.wait()
        except BaseException:
            stop_child(child)
            raise
        if status:
            stop_child(child)
            raise ToolError(f"{args[0]} exited with status {status}", status)
        if blocked:
            stop_child(child)
            raise ToolError("Unallowlisted WARN or WARNING in command output")


def atomic_write(path, data, mode=None):
    path = Path(path)
    if path.is_symlink():
        raise ToolError(f"Refusing to replace symlink: {path}")
    if mode is None:
        mode = path.stat().st_mode & 0o777 if path.exists() else 0o600
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as output:
            output.write(data.encode() if isinstance(data, str) else data)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temporary, mode)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def digest(path):
    value = hashlib.sha256()
    with Path(path).open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


class DirectoryLock:
    """移动输入通过 mkdir 实现跨进程互斥；中断后保留所有权记录。"""
    def __init__(self, path):
        self.path = Path(path)

    def __enter__(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        try:
            self.path.mkdir()
        except FileExistsError:
            raise ToolError(f"Inputs are in use: {self.path}; inspect the owning processes before removing a stale lock") from None
        return self

    def __exit__(self, kind, value, traceback):
        if kind is None or issubclass(kind, Exception):
            self.path.rmdir()
