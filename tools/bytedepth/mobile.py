"""移动包内操作；原生构建与 npm 操作共用输入锁。"""

import os
from pathlib import Path
import shutil

from . import ROOT
from .process import DirectoryLock, ToolError, atomic_write, capture, run


def native(root, args):
    if not args:
        raise ToolError("Mobile run requires a command after --", 2)
    mobile = Path(root) / "apps/mobile"
    with DirectoryLock(mobile / ".expo/toolchain.lock"):
        run(args, mobile, warnings=True)


def quality(root=ROOT):
    root = Path(root)
    mobile = root / "apps/mobile"
    node = str(root / "tools/node.mjs")
    with DirectoryLock(mobile / ".expo/toolchain.lock"):
        if (mobile / "node_modules").is_symlink():
            raise ToolError("Mobile node_modules must belong to this package")
        expected = "v" + (mobile / ".nvmrc").read_text().strip()
        if capture(["node", "--version"], mobile).strip() != expected:
            raise ToolError("Use the pinned mobile Node version in apps/mobile/.nvmrc")
        environment = dict(os.environ)
        environment.pop("EXPO_PUBLIC_RELEASE_CHANNEL", None)
        run(["npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund"], mobile, env=environment, warnings=True)
        if not all(os.access(mobile / "node_modules/.bin" / name, os.X_OK) for name in ("jest", "tsc")):
            raise ToolError("Mobile dependencies are incomplete")
        if (mobile / "coverage").exists():
            shutil.rmtree(mobile / "coverage")
        for args in (["npm", "run", "typecheck"], ["npm", "run", "lint"], ["npm", "test", "--", "--runInBand", "--coverage"], ["node", node, "mobile", "checks", "coverage", str(mobile)], ["node", node, "assets", "mobile", "check"]):
            run(args, mobile, env=environment, warnings=True)
        for channel in ("preview", "production"):
            env = dict(environment, EXPO_PUBLIC_RELEASE_CHANNEL=channel)
            value = capture(["npm", "exec", "--no", "--", "expo", "config", "--type", "public", "--json"], mobile, env=env)
            file = mobile / f".expo/mobile-{channel}-config.json"
            atomic_write(file, value)
            run(["node", node, "mobile", "checks", "config", str(file)], mobile, env=env, warnings=True)
