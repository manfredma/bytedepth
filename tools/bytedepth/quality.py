"""本地质量操作；Host 执行不属于此模块职责。"""

import os
from pathlib import Path
import re
import shutil
from types import SimpleNamespace

from . import ROOT
from .process import ToolError, capture, run


def java_home():
    candidates = [os.environ.get("JAVA_HOME_25_X64"), os.environ.get("JAVA_HOME")]
    if Path("/usr/libexec/java_home").is_file():
        candidates.append(capture(["/usr/libexec/java_home", "-v", "25"], ROOT).strip())
    for candidate in filter(None, candidates):
        executable = Path(candidate) / "bin/java"
        if executable.is_file() and re.search(r"(?:openjdk|java) 25(?:[.\s]|$)", capture([str(executable), "--version"], ROOT)):
            return str(candidate)
    raise ToolError("Java 25 is required; set JAVA_HOME_25_X64 or JAVA_HOME")


def engineering_tests(root):
    from .__main__ import test
    test(root)


def coverage(root=ROOT):
    root = Path(root)
    base = os.environ.get("COVERAGE_BASE_REF") or capture(["git", "describe", "--tags", "--abbrev=0"], root).strip()
    capture(["git", "rev-parse", "--verify", f"{base}^{{commit}}"], root)
    includes = os.environ.get("COVERAGE_INCLUDES")
    if includes:
        modules = sorted(path.parts[-4] for path in root.glob("bytedepth-*/src/main/java"))
    else:
        paths = []
        for args in (["diff", "--name-only", f"{base}...HEAD", "--"], ["diff", "--name-only", "--"], ["diff", "--cached", "--name-only", "--"]):
            paths.extend(capture(["git", *args], root).splitlines())
        changed = sorted({path for path in paths if re.fullmatch(r"bytedepth-[^/]+/src/main/java/.*\.java", path)})
        if not changed:
            print(f"No changed production Java classes since {base}; no changed coverage to verify.")
            return
        modules = sorted({path.split("/")[0] for path in changed})
        includes = ",".join(path.split("/src/main/java/")[1][:-5] + "*" for path in changed)
    environment = dict(os.environ, JAVA_HOME=java_home())
    wrapper = str(root / "mvnw")
    print(f"Coverage base: {base}\nCoverage includes: {includes}")
    run([wrapper, "clean", "install", "-DskipTests", "-Dsort.skip=true"], root, env=environment, warnings=True)
    run([wrapper, "verify", "-Pchanged-coverage", f"-Dcoverage.includes={includes}", "-Dcoverage.check.skip=true", "-Dsort.skip=true"], root, env=environment, warnings=True)
    for module in modules:
        run([wrapper, "-pl", module, "-Pchanged-coverage", f"-Dcoverage.includes={includes}", "-Dcoverage.check.skip=false", "-Dsort.skip=true", "jacoco:check@check-changed-classes"], root, env=environment, warnings=True)


def quality(root=ROOT):
    from . import evidence, formatting, release
    root = Path(root)
    release.check(root)
    run(["npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund"], root, warnings=True)
    formatting.format_files(root, "all", check=True)
    engineering_tests(root)
    for name in ("target/annotation-unit-evidence", "coverage"):
        shutil.rmtree(root / name, ignore_errors=False) if (root / name).exists() else None
    context = root / ".superpowers/quality/annotation-unit/run.json"
    evidence.begin(context)
    environment = dict(os.environ, JAVA_HOME=java_home())
    wrapper = str(root / "mvnw")
    run([wrapper, "clean", "install", "-DskipTests", "-Dsort.skip=true"], root, env=environment, warnings=True)
    run([wrapper, "test", "-Dsort.skip=true"], root, env=environment, warnings=True)
    coverage(root)
    run(["npm", "test"], root, warnings=True)
    run(["npm", "run", "lint"], root, warnings=True)
    evidence.verify(SimpleNamespace(vitest=root / "target/annotation-unit-evidence/vitest.json", surefire_root=root, coverage=root / "coverage/coverage-final.json", cases=root / "scripts/test-fixtures/annotation/unit-cases.json", run_context=context, seal=context.with_name("seal.json"), record_seal=True))
    evidence.mutations(root)
    from . import mobile
    mobile.quality(root)
    tests = sorted(root.glob("apps/mobile/tools/tests/*.test.mjs"))
    if not tests:
        raise ToolError("No mobile engineering tests discovered")
    run(["node", "--test", *map(str, tests)], root, warnings=True)
    run(["git", "diff", "--check"], root)
