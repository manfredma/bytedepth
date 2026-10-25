"""Exercise real child processes and filesystem failures without Host access."""

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


if __name__ == "__main__":
    unittest.main()
