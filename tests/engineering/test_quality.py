"""Verify the local command boundary without running tools or accessing a host."""

from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.bytedepth import quality
from tools.bytedepth.process import ToolError


class QualityTests(unittest.TestCase):
    def test_order_dependency_install_and_local_only_commands(self):
        events = []
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fixture = root / "scripts/test-fixtures/mobile/example.test.mjs"
            fixture.parent.mkdir(parents=True)
            fixture.touch()
            with patch.object(quality, "run", side_effect=lambda args, *a, **kw: events.append(args)), patch.object(quality, "java_home", return_value="/existing/java25"), patch.object(quality, "engineering_tests", side_effect=lambda *a: events.append(["engineering-tests"])), patch.object(quality, "coverage", side_effect=lambda *a: events.append(["coverage"])), patch("tools.bytedepth.release.check", side_effect=lambda *a: events.append(["release-check"])), patch("tools.bytedepth.formatting.format_files", side_effect=lambda *a, **kw: events.append(["format-check"])), patch("tools.bytedepth.evidence.begin"), patch("tools.bytedepth.evidence.verify"), patch("tools.bytedepth.evidence.mutations"):
                quality.quality(root)
        self.assertEqual(events[0], ["release-check"])
        npm = ["npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund"]
        self.assertLess(events.index(npm), events.index(["format-check"]))
        self.assertLess(events.index(["format-check"]), events.index(["npm", "test"]))
        self.assertLess(events.index(["engineering-tests"]), events.index(["npm", "test"]))
        self.assertIn(["coverage"], events)
        self.assertEqual(events[-1], ["git", "diff", "--check"])
        for args in events:
            self.assertFalse(set(args) & {"ssh", "scp", "sftp", "sudo", "curl", "wget", "test:e2e", "-Pstaging-integration"}, args)
            self.assertFalse(any("deploy/" in str(arg) for arg in args), args)

    def test_failure_stops_before_tests_and_host_commands(self):
        calls = []
        def failed(args, *unused, **kwargs):
            calls.append(args)
            raise ToolError("dependency failure", 19)
        with tempfile.TemporaryDirectory() as directory, patch.object(quality, "run", side_effect=failed), patch("tools.bytedepth.release.check"):
            with self.assertRaises(ToolError) as error:
                quality.quality(Path(directory))
        self.assertEqual(error.exception.status, 19)
        self.assertEqual(len(calls), 1)

    def test_coverage_keeps_wrapper_unit_only_profile_and_all_modules(self):
        calls = []
        with tempfile.TemporaryDirectory() as directory, patch.object(quality, "capture", side_effect=["base\n", "base-sha\n", "bytedepth-app/src/main/java/example/A.java\n", "bytedepth-app/src/main/java/example/B.java\n", ""]), patch.object(quality, "run", side_effect=lambda args, *a, **kw: calls.append(args)), patch.object(quality, "java_home", return_value="/existing/java25"):
            quality.coverage(Path(directory))
        self.assertEqual(len(calls), 3)
        self.assertTrue(all(Path(args[0]).name == "mvnw" for args in calls))
        self.assertIn("-Pchanged-coverage", calls[1])
        self.assertNotIn("-Pstaging-integration", sum(calls, []))
        self.assertIn("-Dcoverage.includes=example/A*,example/B*", calls[-1])


if __name__ == "__main__":
    unittest.main()
