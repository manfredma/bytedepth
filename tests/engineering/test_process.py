"""不访问 Host，验证真实子进程和文件系统失败。"""

from contextlib import redirect_stdout
import importlib
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class ProcessTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def api(self):
        return importlib.import_module("tools.bytedepth.process")

    def test_cli_help_and_bad_command(self):
        result = subprocess.run([sys.executable, "-m", "tools.bytedepth", "--help"], capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(b"quality", result.stdout)
        result = subprocess.run([sys.executable, "-m", "tools.bytedepth", "not-a-command"], capture_output=True)
        self.assertEqual(result.returncode, 2)

    def test_empty_discovery_is_failure(self):
        api = self.api()
        commands = importlib.import_module("tools.bytedepth.__main__")
        directory = self.root / "tests/engineering"
        directory.mkdir(parents=True)
        with self.assertRaisesRegex(api.ToolError, "No engineering tests"):
            commands.test(self.root)

    def test_child_failure_preserves_exit_status(self):
        api = self.api()
        with self.assertRaises(api.ToolError) as failed:
            api.run([sys.executable, "-c", "raise SystemExit(17)"], self.root)
        self.assertEqual(failed.exception.status, 17)

    def test_arguments_are_not_shell_code(self):
        api = self.api()
        literal = "a space; $(touch INJECTED) `touch INJECTED`"
        result = api.capture([sys.executable, "-c", "import sys; print(sys.argv[1])", literal], self.root)
        self.assertEqual(result.strip(), literal)
        self.assertFalse((self.root / "INJECTED").exists())

    def test_capture_rejects_warning_on_successful_stderr(self):
        api = self.api()
        with self.assertRaises(api.ToolError):
            api.capture([sys.executable, "-c", "import sys; print('WARNING hidden', file=sys.stderr); print('{}')"], self.root)

    def test_warning_allowlist_is_narrow(self):
        warnings = self.api().warning_lines
        self.assertEqual(warnings("[WARNING] issue for org.javassist:javassist:jar:3.21.0-GA during collection"), [])
        message = "WARN 123 --- [bytedepth] [exec-3] o.a.c.util.SessionIdGeneratorBase : Creation of SecureRandom instance for session ID generation using [SHA1PRNG] took [109] milliseconds."
        self.assertEqual(warnings(message), [])
        for bad in ["WARN other warning", message.replace("109", "1000"), message.replace("109", "100"), message.replace("SHA1PRNG", "other"), message + " WARNING failure", message.replace("SessionIdGeneratorBase", "other.Logger")]:
            with self.subTest(bad=bad):
                self.assertTrue(warnings(bad))

    def test_npm_deprecation_exception_matches_only_reviewed_notice(self):
        warning = "npm warn deprecated glob@7.2.3: Glob versions prior to v9 are no longer supported"
        warnings = self.api().warning_lines
        self.assertEqual(warnings(warning), [])
        self.assertTrue(warnings(warning.replace("7.2.3", "8.0.0")))
        self.assertTrue(warnings(warning + " WARNING unexpected failure"))

    def test_log_is_redacted_and_unknown_warning_rejected(self):
        api = self.api()
        log = self.root / "run.log"
        with redirect_stdout(io.StringIO()), self.assertRaises(api.ToolError):
            api.run([sys.executable, "-c", "print('WARN secret-value')"], self.root, log=log, secrets=("secret-value",), warnings=True)
        self.assertIn("[REDACTED]", log.read_text())
        self.assertNotIn("secret-value", log.read_text())

    def test_log_open_failure_does_not_start_child(self):
        api = self.api()
        with self.assertRaises(OSError):
            api.run([sys.executable, "-c", "open('started','w').close()"], self.root, log=self.root / "missing" / "log")
        self.assertFalse((self.root / "started").exists())

    def test_atomic_write_preserves_mode_and_rejects_symlink(self):
        api = self.api()
        file = self.root / "file with spaces"
        file.write_text("old")
        file.chmod(0o750)
        api.atomic_write(file, b"new")
        self.assertEqual(file.read_bytes(), b"new")
        self.assertEqual(file.stat().st_mode & 0o777, 0o750)
        link = self.root / "link"
        link.symlink_to(file)
        with self.assertRaises(api.ToolError):
            api.atomic_write(link, b"wrong")
        self.assertEqual(file.read_bytes(), b"new")

    def test_lock_is_exclusive_and_released_after_failure(self):
        api = self.api()
        lock = self.root / "input.lock"
        with self.assertRaisesRegex(ValueError, "child failure"):
            with api.DirectoryLock(lock):
                with self.assertRaises(api.ToolError):
                    with api.DirectoryLock(lock):
                        self.fail("entered a held lock")
                raise ValueError("child failure")
        self.assertFalse(lock.exists())

    def test_interrupted_lock_is_preserved(self):
        api = self.api()
        lock = self.root / "input.lock"
        with self.assertRaises(KeyboardInterrupt):
            with api.DirectoryLock(lock):
                raise KeyboardInterrupt()
        self.assertTrue(lock.exists())

    def child_fixture(self, exit_parent=False):
        import time
        pidfile = self.root / 'descendant.pid'
        child = "import os,signal,time,sys; from pathlib import Path; signal.signal(signal.SIGTERM, signal.SIG_IGN); Path(sys.argv[1]).write_text(str(os.getpid())); time.sleep(60)"
        parent = "import subprocess,sys,time; from pathlib import Path; subprocess.Popen([sys.executable,'-c',sys.argv[2],sys.argv[1]]); p=Path(sys.argv[1]);\nwhile not p.exists(): time.sleep(.01)\nprint('READY',flush=True)\n" + ("raise SystemExit(0)" if exit_parent else "time.sleep(60)")
        return [sys.executable, '-c', parent, str(pidfile), child], pidfile

    def descendant_running(self, pid):
        result = subprocess.run(['ps', '-o', 'stat=', '-p', str(pid)], capture_output=True, text=True)
        return bool(result.stdout.strip()) and not result.stdout.strip().startswith('Z')

    def test_stop_child_terminates_group_after_leader_has_exited(self):
        import os
        import signal
        import time
        api = self.api()
        args, pidfile = self.child_fixture(exit_parent=True)
        parent = subprocess.Popen(args, start_new_session=True, stdout=subprocess.DEVNULL)
        try:
            parent.wait(timeout=5)
            pid = int(pidfile.read_text())
            api.stop_child(parent)
            time.sleep(.05)
            self.assertFalse(self.descendant_running(pid))
        finally:
            try:
                os.killpg(parent.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            parent.wait()

    def test_log_write_failure_stops_descendants_before_unlock(self):
        import os
        import signal
        import time
        from unittest.mock import patch
        api = self.api()
        args, pidfile = self.child_fixture()
        class BrokenLog(io.StringIO):
            def write(self, value):
                raise OSError('log sink failed')
        try:
            with patch.object(Path, 'open', return_value=BrokenLog()), redirect_stdout(io.StringIO()):
                with self.assertRaises(OSError):
                    with api.DirectoryLock(self.root / 'inputs.lock'):
                        api.run(args, self.root, log=self.root / 'output.log')
            time.sleep(.05)
            self.assertFalse(self.descendant_running(int(pidfile.read_text())))
            self.assertFalse((self.root / 'inputs.lock').exists())
        finally:
            if pidfile.exists():
                try:
                    os.kill(int(pidfile.read_text()), signal.SIGKILL)
                except ProcessLookupError:
                    pass

    def test_failed_command_stops_pipe_detached_descendant_before_unlock(self):
        import os
        import signal
        import time
        api = self.api()
        args, pidfile = self.child_fixture(exit_parent=True)
        args[2] = args[2].replace("subprocess.Popen([sys.executable,'-c',sys.argv[2],sys.argv[1]])", "subprocess.Popen([sys.executable,'-c',sys.argv[2],sys.argv[1]], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)").replace('SystemExit(0)', 'SystemExit(17)')
        try:
            with redirect_stdout(io.StringIO()), self.assertRaises(api.ToolError) as failure:
                with api.DirectoryLock(self.root / 'inputs.lock'):
                    api.run(args, self.root)
            self.assertEqual(17, failure.exception.status)
            time.sleep(.05)
            self.assertFalse(self.descendant_running(int(pidfile.read_text())))
        finally:
            if pidfile.exists():
                try:
                    os.kill(int(pidfile.read_text()), signal.SIGKILL)
                except ProcessLookupError:
                    pass


if __name__ == "__main__":
    unittest.main()
