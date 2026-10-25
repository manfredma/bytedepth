"""Formatting scope and pinned tools, shared by apply and read-only checks."""

import fnmatch
import hashlib
import os
from pathlib import Path
import platform
import subprocess
import urllib.request

from .process import ToolError, atomic_write, capture, run

EXTENSIONS = {".java", ".js", ".mjs", ".cjs", ".jsx", ".ts", ".tsx", ".html", ".css", ".scss", ".json", ".yml", ".yaml", ".md", ".sh"}
SHFMT_VERSION = "3.11.0"
SHFMT_HASHES = {
    ("darwin", "amd64"): "810a76cb7c78351e021c8025f344b12149d8426ce51609a179af68109ed5698e",
    ("darwin", "arm64"): "af206d234dff5d05d9ac355529b2b33a7a78e13fab9b59db777746aab3e72530",
    ("linux", "amd64"): "1904ec6bac715c1d05cd7f6612eec8f67a625c3749cb327e5bfb4127d09035ff",
    ("linux", "arm64"): "b3976121710fd4b12bf641b0a7fb2686da598fb0da9f148c641b61b54cfa3407",
}


def ignored(root, path):
    relative = Path(os.path.relpath(path, root)).as_posix()
    ignore = Path(root) / ".prettierignore"
    patterns = ignore.read_text().splitlines() if ignore.exists() else []
    for pattern in patterns:
        pattern = pattern.strip()
        if not pattern or pattern.startswith("#"):
            continue
        if pattern.endswith("/"):
            directory = pattern.rstrip("/")
            if "/" in directory:
                if relative == directory or relative.startswith(directory + "/"):
                    return True
            elif directory in Path(relative).parts:
                return True
        else:
            # gitignore's **/ also matches zero directory segments.
            variants = {pattern}
            pending = [pattern]
            while pending:
                item = pending.pop()
                for index in range(len(item)):
                    if item[index:index + 3] == "**/":
                        variant = item[:index] + item[index + 3:]
                        if variant not in variants:
                            variants.add(variant)
                            pending.append(variant)
            if any(fnmatch.fnmatch(relative if "/" in pattern else Path(relative).name, item) for item in variants):
                return True
    return False


def collect(root, scope, paths=()):
    root = Path(root).resolve()
    if scope == "all":
        selected = [root]
    elif scope == "paths":
        selected = [Path(p) if Path(p).is_absolute() else root / p for p in paths]
    elif scope == "changed":
        selected = [root / p for p in (capture(["git", "diff", "--name-only", "--diff-filter=ACMR", "HEAD"], root) + capture(["git", "ls-files", "--others", "--exclude-standard"], root)).splitlines()]
    else:
        raise ToolError("Unknown formatting scope", 2)
    files = set()

    def add(file):
        if file.is_file() and not file.is_symlink() and file.suffix in EXTENSIONS and not ignored(root, file):
            files.add(file.absolute())

    for path in selected:
        if path.is_symlink() or ignored(root, path):
            continue
        if path.is_dir():
            for directory, names, filenames in os.walk(path, followlinks=False):
                names[:] = [name for name in names if not (Path(directory) / name).is_symlink() and not ignored(root, Path(directory) / name)]
                for name in filenames:
                    add(Path(directory) / name)
        else:
            add(path)
    return sorted(files)


def shfmt_binary():
    system = platform.system().lower()
    machine = platform.machine()
    arch = {"x86_64": "amd64", "aarch64": "arm64", "arm64": "arm64"}.get(machine)
    expected = SHFMT_HASHES.get((system, arch))
    if not expected:
        raise ToolError(f"Unsupported shfmt platform: {system}/{machine}")
    asset = f"shfmt_v{SHFMT_VERSION}_{system}_{arch}"
    cache = Path(os.environ.get("FORMATTER_CACHE_DIR", str(Path(os.environ.get("XDG_CACHE_HOME", str(Path.home() / ".cache"))) / "project-formatters")))
    binary = cache / "shfmt" / SHFMT_VERSION / f"{system}-{machine}" / asset
    if binary.is_symlink():
        raise ToolError("Formatter cache must not be a symlink")
    if binary.exists():
        data = binary.read_bytes()
    else:
        url = f"https://github.com/mvdan/sh/releases/download/v{SHFMT_VERSION}/{asset}"
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ToolError(f"shfmt checksum mismatch: {asset}")
    if not binary.exists():
        binary.parent.mkdir(parents=True, exist_ok=True)
        atomic_write(binary, data, mode=0o755)
    return binary


def format_files(root, scope, paths=(), check=False):
    root = Path(root).resolve()
    files = collect(root, scope, paths)
    java = [file for file in files if file.suffix == ".java"]
    prettier = root / "node_modules/.bin/prettier"
    for file in files:
        if file.suffix == ".java":
            continue
        if file.suffix == ".sh":
            result = subprocess.run([str(shfmt_binary()), "-i", "2", "-ci", "-bn", "-sr"], input=file.read_bytes(), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        else:
            if not prettier.is_file():
                raise ToolError("Prettier unavailable; run npm ci --ignore-scripts --no-audit --no-fund")
            result = subprocess.run([str(prettier), str(file), "--config", str(root / ".prettierrc.json"), "--ignore-path", str(root / ".prettierignore")], cwd=root, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise ToolError(result.stderr.decode(errors="replace"), result.returncode)
        if result.stdout != file.read_bytes():
            if check:
                raise ToolError(f"Formatting violation: {file}")
            atomic_write(file, result.stdout)
    if java:
        from .quality import java_home
        environment = dict(os.environ, JAVA_HOME=java_home())
        goal = "check" if check else "apply"
        run([str(root / "mvnw"), f"com.diffplug.spotless:spotless-maven-plugin:3.10.2:{goal}", "-DspotlessFiles=" + ",".join(map(str, java))], root, env=environment, warnings=True)
    print(f"[format-{'check' if check else 'apply'}] {len(files)} supported file(s) processed.")
