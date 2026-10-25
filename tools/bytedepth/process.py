"""Shared subprocess, output policy and file operations; no deployment orchestration."""

from contextlib import ExitStack
import hashlib
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile


class ToolError(Exception):
    def __init__(self, message, status=1):
        super().__init__(message)
        self.status = status if 0 < status < 126 else 1


def warning_lines(text):
    """TD-0006 allowlist, shared by local commands and formal Host logs."""
    blocked = []
    for number, line in enumerate(text.splitlines(), 1):
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
    return result.stdout


def redact(text, secrets):
    for secret in secrets:
        if secret:
            text = text.replace(secret, "[REDACTED]")
    return text


def stop_child(child):
    if child.poll() is not None:
        return
    os.killpg(child.pid, signal.SIGTERM)
    try:
        child.wait(timeout=5)
    except subprocess.TimeoutExpired:
        os.killpg(child.pid, signal.SIGKILL)
        child.wait()


def run(args, cwd, env=None, log=None, secrets=(), warnings=False):
    """Stream a child, fail on exit/log errors, and never evaluate a shell string."""
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
            raise ToolError(f"{args[0]} exited with status {status}", status)
        if blocked:
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
    """Mobile inputs use mkdir across processes; interrupted ownership is retained."""
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
