"""One command surface for project tools, runnable without npm dependencies."""

import argparse
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
    for name in ("quality", "format", "coverage", "release", "assets", "mobile", "host"):
        commands.add_parser(name, help=f"{name} operations")
    args = parser.parse_args(argv)
    try:
        if args.command == "test":
            test()
        else:
            raise ToolError(f"Command migration pending: {args.command}", 2)
    except (ToolError, OSError, ValueError) as error:
        print(f"Refusing: {error}", file=sys.stderr)
        return getattr(error, "status", 1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
