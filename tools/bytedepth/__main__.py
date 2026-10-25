"""One command surface for project tools, runnable without npm dependencies."""

import argparse
from pathlib import Path
import sys
import unittest

from . import ROOT
from .process import ToolError


def test(root=ROOT):
    suite = unittest.defaultTestLoader.discover(str(root / "tests/engineering"))
    if not suite.countTestCases():
        raise ToolError("No engineering tests discovered")
    if not unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful():
        raise ToolError("Engineering tests failed")


def main(argv=None):
    parser = argparse.ArgumentParser(description="ByteDepth project tools")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("test", help="Run offline engineering tests")
    release = commands.add_parser("release", help="Check and prepare release metadata without deploying")
    actions = release.add_subparsers(dest="action", required=True)
    check = actions.add_parser("check")
    check.add_argument("--target", default="HEAD")
    check.add_argument("--base", default="origin/main")
    check.add_argument("--mode", choices=("candidate", "frozen-candidate", "release"), default="candidate")
    check.add_argument("--expected-release", default="")
    prepare = actions.add_parser("prepare")
    kind = prepare.add_mutually_exclusive_group()
    kind.add_argument("--kind", choices=("major", "minor", "patch"))
    for name in ("major", "minor", "patch"):
        kind.add_argument("--" + name, dest="kind", action="store_const", const=name)
    kind.add_argument("--version")
    prepare.add_argument("--date")
    prepare.add_argument("--changelog", type=Path, default=Path("docs/releases/CHANGELOG.md"))
    order = actions.add_parser("order")
    order.add_argument("changelog", nargs="?", type=Path, default=Path("docs/releases/CHANGELOG.md"))
    source = actions.add_parser("source")
    source.add_argument("pr")
    source.add_argument("sha")
    change = actions.add_parser("change")
    change.add_argument("--target", required=True)
    change.add_argument("--base", required=True)
    formatting = commands.add_parser("format", help="Format explicit files, all files or changed files")
    formatting.add_argument("--check", action="store_true")
    scope = formatting.add_mutually_exclusive_group()
    scope.add_argument("--all", dest="scope", action="store_const", const="all")
    scope.add_argument("--changed", dest="scope", action="store_const", const="changed")
    formatting.add_argument("paths", nargs="*")
    assets = commands.add_parser("assets", help="Build or verify pinned generated assets")
    assets.add_argument("target", choices=("prism", "hypothesis", "mobile"))
    assets.add_argument("action", choices=("build", "check"))
    mobile = commands.add_parser("mobile", help="Mobile quality, native inputs, receipts and fixtures")
    mobile.add_argument("action", choices=("quality", "run", "verify-delivery", "fixtures"))
    mobile.add_argument("arguments", nargs=argparse.REMAINDER)
    for name in ("quality", "coverage", "host"):
        commands.add_parser(name, help=f"{name} operations")
    args = parser.parse_args(argv)
    try:
        if args.command == "test":
            test()
        elif args.command == "format":
            from .formatting import format_files
            if bool(args.paths) == bool(args.scope):
                raise ToolError("Choose explicit paths, --all or --changed", 2)
            format_files(ROOT, args.scope or "paths", args.paths, args.check)
        elif args.command in ("quality", "coverage"):
            from . import quality
            {"quality": quality.quality, "coverage": quality.coverage}[args.command](ROOT)
        elif args.command == "assets":
            from .process import run
            if args.target == "mobile":
                command = ["node", "apps/mobile/tools/assets.mjs"] + (["--check"] if args.action == "check" else [])
            else:
                command = ["node", "tools/assets.mjs", args.target, args.action]
            run(command, ROOT, warnings=True)
        elif args.command == "mobile":
            from . import mobile
            from .process import run
            arguments = args.arguments[1:] if args.arguments[:1] == ["--"] else args.arguments
            if args.action == "quality":
                if arguments:
                    raise ToolError("mobile quality takes no extra arguments", 2)
                mobile.quality(ROOT)
            elif args.action == "run":
                mobile.native(ROOT, arguments)
            else:
                name = "delivery" if args.action == "verify-delivery" else "fixtures"
                run(["node", f"apps/mobile/tools/{name}.mjs", *arguments], ROOT, warnings=True)
        elif args.command == "release":
            from . import release
            if args.action == "check":
                release.check(Path.cwd(), args.target, args.base, args.mode, args.expected_release)
            elif args.action == "prepare":
                version = release.prepare(args.changelog, args.kind or "minor", args.version, args.date)
                print(f"Prepared release {version}; commit the Changelog before creating a candidate.")
            elif args.action == "order":
                release.check_order(args.changelog)
            elif args.action == "source":
                release.check_source(Path.cwd(), args.pr, args.sha)
            elif args.action == "change":
                release.check_change(Path.cwd(), args.target, args.base)
            print("Release metadata check passed.")
        else:
            raise ToolError(f"Command migration pending: {args.command}", 2)
    except (ToolError, OSError, ValueError) as error:
        print(f"Refusing: {error}", file=sys.stderr)
        return getattr(error, "status", 1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
