"""One Changelog model for preparation, candidate gates and legacy metadata."""

from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
import re

from .process import ToolError, atomic_write, capture

CHANGELOG = "docs/releases/CHANGELOG.md"
VERSION = r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
HEADING = re.compile(r"## \[v(" + VERSION + r")\] - \d{4}-\d{2}-\d{2}\s*")
CATEGORIES = {"Added", "Changed", "Deprecated", "Removed", "Fixed", "Security", "Compatibility"}


def version_tuple(value):
    if not re.fullmatch(VERSION, value):
        raise ToolError(f"Invalid SemVer: {value}", 2)
    return tuple(map(int, value.split(".")))


@dataclass
class Section:
    title: str
    body: list
    version: str = ""

    def categorized(self, strict=False):
        active = False
        found = False
        for line in self.body:
            if line.startswith("### "):
                active = line[4:].strip() in CATEGORIES
            if re.match(r"\s*-\s+", line):
                item = re.sub(r"^\s*-\s+", "", line).strip()
                meaningful = bool(item) and not re.fullmatch(r"<!--.*-->", item)
                if strict and (not active or not meaningful):
                    return False
                found |= active and meaningful
        return found

    def field(self, name):
        values = [line for line in self.body if re.match(r"\*\*" + re.escape(name) + r"\*\*[：:]", line)]
        if not values:
            return ""
        match = re.search(r"`v(" + VERSION + r")`", values[0])
        if len(values) != 1 or not match:
            raise ToolError(f"Invalid or duplicate release {name}")
        return "v" + match[1]


class Changelog:
    def __init__(self, text):
        self.text = text
        self.sections = []
        for line in text.splitlines():
            if line.startswith("## "):
                if line == "## 记录模板":
                    if len(self.sections) < 2:
                        raise ToolError("Changelog template must follow release sections")
                    break
                match = HEADING.fullmatch(line)
                section = Section(line, [], match[1] if match else "")
                if not self.sections:
                    if line != "## Unreleased":
                        raise ToolError("## Unreleased must be the first section")
                elif not match:
                    raise ToolError(f"Invalid or repeated Changelog section: {line}")
                self.sections.append(section)
            elif self.sections:
                self.sections[-1].body.append(line)
        if not self.sections:
            raise ToolError("Missing ## Unreleased section")
        versions = [version_tuple(s.version) for s in self.sections[1:]]
        if any(a <= b for a, b in zip(versions, versions[1:])):
            raise ToolError("Release sections must be in strict SemVer descending order")

    @property
    def pending(self):
        return self.sections[0]

    @property
    def latest(self):
        return self.sections[1] if len(self.sections) > 1 else None


def read(path):
    path = Path(path)
    if not path.is_file() or path.is_symlink():
        raise ToolError(f"Missing or symlinked Changelog: {path}")
    return Changelog(path.read_text())


def check_order(path):
    read(path)


def prepare(path, kind="minor", version=None, date=None):
    changelog = read(path)
    if not changelog.pending.categorized(strict=True):
        raise ToolError("Unreleased must contain a non-empty categorized bullet")
    if kind not in ("minor", "patch", "major"):
        raise ToolError("Invalid release kind", 2)
    if version:
        if not version.startswith("v"):
            raise ToolError("Explicit version requires v prefix", 2)
        version_tuple(version[1:])
        next_version = version
    else:
        if not changelog.latest:
            raise ToolError("Existing release required, or supply --version")
        parts = list(version_tuple(changelog.latest.version))
        index = {"major": 0, "minor": 1, "patch": 2}[kind]
        parts[index] += 1
        parts[index + 1:] = [0] * (2 - index)
        next_version = "v" + ".".join(map(str, parts))
    release_date = date or datetime.now(timezone.utc).date().isoformat()
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", release_date):
        raise ToolError("Date must be YYYY-MM-DD", 2)
    datetime.strptime(release_date, "%Y-%m-%d")
    result = changelog.text.replace("## Unreleased\n", f"## Unreleased\n\n## [{next_version}] - {release_date}\n", 1)
    Changelog(result)  # Validate before writing; rejection must not damage the input.
    atomic_write(path, result)
    return next_version


def git(root, *args):
    return capture(["git", *args], root).strip()


def commit(root, ref):
    return git(root, "rev-parse", "--verify", f"{ref}^{{commit}}")


def non_runtime(path):
    return (path.startswith(("docs/", "tests/", "frontend/tests/", "src/test/"))
            or "/src/test/" in path
            or path == "AGENTS.md"
            or re.fullmatch(r"(?:README|LICENSE|NOTICE|CONTRIBUTING)(?:\..*)?", path))


def frozen(root, changelog, baseline, base_commit, expected="", allow_same=False):
    latest = changelog.latest
    if not latest or any(line.strip() for line in changelog.pending.body) or not latest.categorized(strict=True):
        raise ToolError("Frozen Changelog requires empty Unreleased and categorized release notes")
    current = version_tuple(latest.version)
    previous = version_tuple(baseline) if baseline else (0, 0, 0)
    if current < previous or (current == previous and not allow_same):
        raise ToolError("Frozen release must be newer than the baseline")
    if expected and latest.version != expected:
        raise ToolError("Frozen release differs from expected release")
    tag = latest.field("Tag")
    if tag and tag != "v" + latest.version:
        raise ToolError("Release version and Tag differ")
    rollback = latest.field("回滚基线")
    if rollback:
        if not version_tuple(rollback[1:]) < current or version_tuple(rollback[1:]) > previous:
            raise ToolError("Rollback version must precede candidate and not exceed baseline")
        if git(root, "cat-file", "-t", "refs/tags/" + rollback) != "tag":
            raise ToolError("Rollback must be an annotated tag")
        git(root, "merge-base", "--is-ancestor", commit(root, "refs/tags/" + rollback), base_commit)


def check(root, target="HEAD", base="origin/main", mode="candidate", expected_release=""):
    if mode not in ("candidate", "frozen-candidate", "release"):
        raise ToolError("Invalid release check mode", 2)
    if expected_release:
        version_tuple(expected_release)
    if mode == "release" and not expected_release:
        raise ToolError("Release mode requires --expected-release", 2)
    target_sha, base_sha = commit(root, target), commit(root, base)
    changelog = read(Path(root) / CHANGELOG) if target == "HEAD" else Changelog(git(root, "show", f"{target_sha}:{CHANGELOG}"))
    if mode == "release":
        tags = git(root, "tag", "--merged", base_sha, "--sort=-version:refname").splitlines()
        stable = next((tag for tag in tags if re.fullmatch("v" + VERSION, tag)), "")
        if not stable:
            raise ToolError("No prior stable release tag is reachable from base")
        baseline = stable[1:]
    else:
        base_notes = Changelog(git(root, "show", f"{base_sha}:{CHANGELOG}"))
        latest = base_notes.latest
        baseline = latest.version if latest else ""
        if latest and latest.field("Tag") not in ("", "v" + baseline):
            raise ToolError("Base Changelog version and Tag differ")
    if mode != "candidate":
        frozen(root, changelog, baseline, base_sha, expected_release)
        return
    paths = git(root, "diff", "--name-only", f"{base_sha}...{target_sha}", "--").splitlines()
    if target == "HEAD":
        paths += git(root, "diff", "--name-only", base_sha, "--").splitlines()
        paths += git(root, "diff", "--cached", "--name-only", "--").splitlines()
        paths += git(root, "ls-files", "--others", "--exclude-standard").splitlines()
    paths = set(filter(None, paths))
    has_bullet = any(re.match(r"\s*-\s+", line) for line in changelog.pending.body)
    if not paths:
        if not has_bullet and changelog.latest:
            frozen(root, changelog, baseline, base_sha, allow_same=True)
        return
    if all(non_runtime(path) for path in paths):
        return
    if not has_bullet and changelog.latest:
        frozen(root, changelog, baseline, base_sha, expected_release)
    elif not changelog.pending.categorized():
        raise ToolError("Runtime changes require a non-empty categorized Unreleased entry")


def check_change(root, target, base):
    target_sha, base_sha = commit(root, target), commit(root, base)
    if target_sha == base_sha or not git(root, "diff", "--name-only", f"{base_sha}...{target_sha}", "--", CHANGELOG):
        raise ToolError("Staging candidate must change Changelog relative to base")


def check_source(root, pr, sha):
    if not re.fullmatch(r"[1-9][0-9]*", str(pr)) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ToolError("Candidate requires PR number and full 40-character SHA", 2)
    local = git(root, "rev-parse", "HEAD")
    remote = capture(["gh", "pr", "view", str(pr), "--json", "headRefOid", "--jq", ".headRefOid"], root).strip()
    if sha != local or sha != remote:
        raise ToolError("Candidate SHA must equal local HEAD and PR head")
