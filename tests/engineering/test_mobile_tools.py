"""使用模拟安装工具验证移动端输入锁和质量失败边界。"""

import importlib
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

from tools.bytedepth import ROOT
from tools.bytedepth.process import ToolError


class MobileToolTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.mobile = self.root / "apps/mobile"
        self.mobile.mkdir(parents=True)
        (self.mobile / ".nvmrc").write_text("22.22.1\n")

    def api(self):
        return importlib.import_module("tools.bytedepth.mobile")

    def install(self):
        directory = self.mobile / "node_modules/.bin"
        directory.mkdir(parents=True, exist_ok=True)
        for name in ["tsc", "jest"]:
            file = directory / name
            file.touch()
            file.chmod(0o755)

    def test_stage_failure_and_missing_dependencies_release_lock(self):
        api = self.api()
        stages = ["ci", "typecheck", "lint", "test", "coverage", "assets", "exec", "config"]
        for stage in stages:
            self.install()
            def run(args, *unused, **kwargs):
                if stage in " ".join(map(str, args)):
                    raise ToolError("stage failed", 17)
            def capture(args, *unused, **kwargs):
                if args == ["node", "--version"]:
                    return "v22.22.1\n"
                if stage == "exec":
                    raise ToolError("config failed", 17)
                return "{}"
            with self.subTest(stage=stage), patch.object(api, "run", side_effect=run), patch.object(api, "capture", side_effect=capture):
                with self.assertRaises(ToolError):
                    api.quality(self.root)
                self.assertFalse((self.mobile / ".expo/toolchain.lock").exists())

    def test_shared_dependencies_and_wrong_node_are_rejected(self):
        api = self.api()
        (self.mobile / "node_modules").symlink_to(self.root)
        with self.assertRaises(ToolError):
            api.quality(self.root)
        (self.mobile / "node_modules").unlink()
        with patch.object(api, "capture", return_value="v20.0.0"), self.assertRaises(ToolError):
            api.quality(self.root)
        with patch.object(api, "capture", return_value="v22.22.1"), patch.object(api, "run"), self.assertRaises(ToolError):
            api.quality(self.root)

    def test_existing_lock_prevents_native_execution(self):
        api = self.api()
        lock = self.mobile / ".expo/toolchain.lock"
        lock.mkdir(parents=True)
        target = self.root / "must-not-run"
        with self.assertRaises(ToolError):
            api.native(self.root, [sys.executable, "-c", f"open({str(target)!r},'w').close()"])
        self.assertFalse(target.exists())

    def test_native_command_releases_lock_after_normal_failure(self):
        api = self.api()
        with self.assertRaises(ToolError):
            api.native(self.root, [sys.executable, "-c", "raise SystemExit(17)"])
        self.assertFalse((self.mobile / ".expo/toolchain.lock").exists())

    def test_signal_preserves_lock_and_blocks_overlapping_mutation(self):
        self.api()
        marker = self.root / "child.pid"
        program = "import os,pathlib,time;pathlib.Path(__import__('sys').argv[1]).write_text(str(os.getpid()));time.sleep(30)"
        driver = "from tools.bytedepth.mobile import native;from pathlib import Path;import sys;native(Path(sys.argv[1]),sys.argv[2:])"
        child = subprocess.Popen([sys.executable, "-c", driver, str(self.root), sys.executable, "-c", program, str(marker)], cwd=ROOT)
        try:
            deadline = time.monotonic() + 5
            while not marker.exists() and time.monotonic() < deadline:
                time.sleep(.01)
            self.assertTrue(marker.exists())
            pid = int(marker.read_text())
            child.send_signal(signal.SIGTERM)
            child.wait(timeout=5)
            os.kill(pid, 0)
            self.assertTrue((self.mobile / ".expo/toolchain.lock").is_dir())
            with self.assertRaises(ToolError):
                self.api().native(self.root, [sys.executable, "-c", "pass"])
        finally:
            if marker.exists():
                try:
                    os.killpg(int(marker.read_text()), signal.SIGKILL)
                except ProcessLookupError:
                    pass
            if child.poll() is None:
                child.kill()
            child.wait()


if __name__ == "__main__":
    unittest.main()
