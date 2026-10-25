"""使用隔离的真实 Git 历史验证发布元数据行为。"""

import importlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


def notes(version="2.28.0", pending="", fields="", body="### Fixed\n\n- Shipped change."):
    return f"# Changelog\n\n## Unreleased\n\n{pending}\n## [v{version}] - 2026-10-10\n\n{fields}\n{body}\n"


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.file = self.root / "docs/releases/CHANGELOG.md"
        self.file.parent.mkdir(parents=True)
        self.file.write_text(notes())
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "test@example.invalid")
        self.git("config", "user.name", "release-test")
        self.commit()
        self.git("branch", "base")
        self.git("tag", "-a", "v2.28.0", "-m", "baseline")

    def api(self):
        return importlib.import_module("tools.bytedepth.release")

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, stderr=subprocess.PIPE, text=True).strip()

    def commit(self):
        self.git("add", ".")
        self.git("commit", "-qm", "fixture")

    def runtime(self):
        (self.root / "runtime.txt").write_text("runtime change")
        self.commit()

    def test_runtime_requires_notes_and_categorization(self):
        self.runtime()
        api = self.api()
        for pending in ["", "### Added", "### Added\n<!-- later -->", "- Unclassified", "### Unknown\n- Invalid"]:
            self.file.write_text(notes(pending=pending))
            with self.subTest(pending=pending), self.assertRaises(api.ToolError):
                api.check(self.root, base="base")
        self.file.write_text(notes(pending="### Changed\n\n- New behavior."))
        api.check(self.root, base="base")

    def test_docs_and_tests_only_do_not_require_notes(self):
        api = self.api()
        for name in ["docs/guide.md", "AGENTS.md", "tests/test_example.py", "app/src/test/java/Example.java"]:
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("documentation or test")
        api.check(self.root, base="base")

    def test_untracked_and_staged_runtime_files_are_checked(self):
        api = self.api()
        (self.root / "untracked.txt").write_text("change")
        with self.assertRaises(api.ToolError):
            api.check(self.root, base="base")
        self.git("add", "untracked.txt")
        with self.assertRaises(api.ToolError):
            api.check(self.root, base="base")

    def test_same_commit_frozen_main_is_accepted(self):
        self.api().check(self.root, base="HEAD")

    def test_frozen_and_release_require_new_version(self):
        api = self.api()
        for mode in ["frozen-candidate", "release"]:
            with self.subTest(mode=mode), self.assertRaises(api.ToolError):
                api.check(self.root, base="base", mode=mode, expected_release="2.28.0")
        self.file.write_text(notes("2.28.1") + "\n## [v2.28.0] - 2026-10-10\n")
        api.check(self.root, base="base", mode="frozen-candidate")
        api.check(self.root, base="base", mode="release", expected_release="2.28.1")
        with self.assertRaises(api.ToolError):
            api.check(self.root, base="base", mode="release", expected_release="2.28.2")

    def test_frozen_rejects_stale_prose_unclassified_and_empty_notes(self):
        api = self.api()
        for pending, body in [("No pending changes.", "### Fixed\n- Change"), ("### Fixed\n- Stale", "### Fixed\n- Change"), ("", "- Unclassified\n### Fixed"), ("", "### Fixed"), ("", "### Fixed\n- <!-- placeholder -->")]:
            self.file.write_text(notes("2.28.1", pending=pending, body=body))
            with self.subTest(pending=pending, body=body), self.assertRaises(api.ToolError):
                api.check(self.root, base="base", mode="frozen-candidate")

    def test_optional_legacy_metadata_is_validated(self):
        api = self.api()
        self.file.write_text(notes("2.28.1", fields="**Tag**：`v2.28.1`\n**回滚基线**：`v2.28.0`"))
        api.check(self.root, base="base", mode="frozen-candidate")
        self.git("tag", "v2.27.9")
        for field in ["**Tag**：invalid", "**Tag**：`v2.28.0`", "**回滚基线**：invalid", "**回滚基线**：`v2.28.1`", "**回滚基线**：`v2.25.2`", "**回滚基线**：`v2.27.9`"]:
            self.file.write_text(notes("2.28.1", fields=field))
            with self.subTest(field=field), self.assertRaises(api.ToolError):
                api.check(self.root, base="base", mode="frozen-candidate")

    def test_rollback_may_precede_newest_candidate_tag(self):
        api = self.api()
        self.file.write_text(notes("2.29.0"))
        self.commit()
        self.git("tag", "-a", "v2.29.0", "-m", "not deployed")
        self.file.write_text(notes("2.29.1", fields="**回滚基线**：`v2.28.0`"))
        api.check(self.root, base="v2.29.0", mode="release", expected_release="2.29.1")

    def test_invalid_base_metadata_and_missing_refs_fail(self):
        api = self.api()
        with self.assertRaises(api.ToolError):
            api.check(self.root, base="missing")
        self.file.write_text(notes(fields="**Tag**：`v2.27.0`"))
        self.commit()
        with self.assertRaises(api.ToolError):
            api.check(self.root, base="HEAD")

    def test_explicit_target_reads_its_changelog_not_worktree(self):
        api = self.api()
        self.runtime()
        self.file.write_text(notes(pending="### Fixed\n- Uncommitted notes"))
        with self.assertRaises(api.ToolError):
            api.check(self.root, target="main", base="base")
        api.check(self.root, base="base")

    def test_order_and_template_rules(self):
        api = self.api()
        for content in ["# Missing", "## [v1.0.0] - 2026-10-10\n", "## Unreleased\n## Unreleased\n", "## Unreleased\n## 记录模板\n", notes() + "\n## [v2.29.0] - 2026-10-10\n", notes() + "\n## [v2.28.0] - 2026-10-10\n"]:
            self.file.write_text(content)
            with self.subTest(content=content), self.assertRaises(api.ToolError):
                api.check_order(self.file)
        self.file.write_text(notes() + "\n## [v2.27.0] - 2026-10-10\n\n## 记录模板\n## ignored template heading\n")
        api.check_order(self.file)

    def test_prepare_minor_patch_major_and_explicit(self):
        api = self.api()
        for kind, expected in [("minor", "2.29.0"), ("patch", "2.28.1"), ("major", "3.0.0")]:
            self.file.write_text(notes(pending="### Fixed\n- Pending change"))
            result = api.prepare(self.file, kind=kind, date="2026-10-25")
            self.assertEqual(result, "v" + expected)
            self.assertIn(f"## [v{expected}] - 2026-10-25", self.file.read_text())
            self.assertIn("- Pending change", self.file.read_text())
            api.check_order(self.file)
        self.file.write_text("## Unreleased\n\n### Added\n- Initial release.\n")
        self.assertEqual(api.prepare(self.file, version="v1.0.0"), "v1.0.0")

    def test_prepare_failure_does_not_modify_input(self):
        api = self.api()
        for content, kwargs in [(notes(), {}), (notes(pending="- Unclassified"), {}), (notes(pending="### Fixed\n- Item"), {"version": "1.0.0"}), (notes(pending="### Fixed\n- Item"), {"version": "v2.28.0"}), (notes(pending="### Fixed\n- Item"), {"date": "invalid"})]:
            self.file.write_text(content)
            with self.subTest(kwargs=kwargs), self.assertRaises((api.ToolError, ValueError)):
                api.prepare(self.file, **kwargs)
            self.assertEqual(self.file.read_text(), content)

    def test_symlinked_changelog_is_rejected(self):
        api = self.api()
        target = self.root / "actual"
        self.file.rename(target)
        self.file.symlink_to(target)
        with self.assertRaises(api.ToolError):
            api.check_order(self.file)

    def test_staging_change_rejects_equal_or_unchanged_refs(self):
        api = self.api()
        with self.assertRaises(api.ToolError):
            api.check_change(self.root, "HEAD", "base")
        self.runtime()
        with self.assertRaises(api.ToolError):
            api.check_change(self.root, "HEAD", "base")
        self.file.write_text(notes(pending="### Fixed\n- Candidate"))
        self.commit()
        api.check_change(self.root, "HEAD", "base")

    def test_candidate_source_binds_local_and_pr_sha(self):
        api = self.api()
        sha = self.git("rev-parse", "HEAD")
        with patch.object(api, "capture", return_value=sha):
            api.check_source(self.root, "160", sha)
            for pr, supplied in [("0", sha), ("160", sha[:8]), ("160", "f" * 40)]:
                with self.subTest(pr=pr, sha=supplied), self.assertRaises(api.ToolError):
                    api.check_source(self.root, pr, supplied)
        with patch.object(api, "capture", side_effect=[sha, "f" * 40]):
            with self.assertRaises(api.ToolError):
                api.check_source(self.root, "160", sha)


if __name__ == "__main__":
    unittest.main()
