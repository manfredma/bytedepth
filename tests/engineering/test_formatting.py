"""Real pinned formatter fixtures plus deterministic file-scope checks."""

import importlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from tools.bytedepth import ROOT


class FormattingTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="bytedepth format ")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()

    def api(self):
        return importlib.import_module("tools.bytedepth.formatting")

    def test_real_fixtures_are_idempotent_and_preserve_mode(self):
        api = self.api()
        files = []
        for ext in ("json", "sh", "md"):
            source = ROOT / f"scripts/test-fixtures/format/malformed.{ext}"
            target = self.root / f"sample with spaces.{ext}"
            shutil.copyfile(source, target)
            files.append(target)
        files[1].chmod(0o755)
        files[0].write_text('{"name":"ByteDepth","enabled":true}')
        before = [p.read_bytes() for p in files]
        with self.assertRaises(api.ToolError):
            api.format_files(ROOT, "paths", files, check=True)
        self.assertEqual(before, [p.read_bytes() for p in files])
        api.format_files(ROOT, "paths", files)
        after = [p.read_bytes() for p in files]
        self.assertNotEqual(before, after)
        self.assertIn(b'"name": "ByteDepth"', after[0])
        self.assertIn(b'if [ "$enabled" = true ]; then', after[1])
        self.assertIn(b"# Formatting fixture", after[2])
        self.assertEqual(files[1].stat().st_mode & 0o777, 0o755)
        api.format_files(ROOT, "paths", files, check=True)
        api.format_files(ROOT, "paths", files)
        self.assertEqual(after, [p.read_bytes() for p in files])

    def test_generated_paths_are_excluded_for_all_and_explicit_scopes(self):
        api = self.api()
        (self.root / ".prettierignore").write_bytes((ROOT / ".prettierignore").read_bytes())
        generated = ["apps/mobile/.expo/config.json", "apps/mobile/android/generated.json", "apps/mobile/ios/generated.json", "node_modules/a.ts", "target/a.json", ".superpowers/notes.md"]
        sources = ["apps/mobile/src/example.ts", "apps/mobile/plugins/example.cjs"]
        for name in generated + sources:
            file = self.root / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text("invalid content")
        for scope, paths in [("all", ()), ("paths", (self.root,))]:
            self.assertEqual(api.collect(self.root, scope, paths), sorted(self.root / p for p in sources))
        self.assertEqual(api.collect(self.root, "paths", [self.root / p for p in generated]), [])

    def test_byte_exact_goldens_and_server_templates_are_ignored(self):
        api = self.api()
        for name in ["scripts/test-fixtures/annotation/rich-markdown.md", "scripts/test-fixtures/annotation/rich-page.html", "app/src/main/resources/templates/page.html", "nested/package-lock.json", "vendor/library.js"]:
            self.assertTrue(api.ignored(ROOT, ROOT / name), name)

    def test_symlink_cannot_format_an_unselected_external_file(self):
        api = self.api()
        external = self.root / "external.json"
        external.write_text('{"keep":true}')
        link = self.root / "link.json"
        link.symlink_to(external)
        self.assertEqual(api.collect(self.root, "paths", [link]), [])

    def test_unknown_and_missing_scope_are_rejected(self):
        api = self.api()
        with self.assertRaises(api.ToolError):
            api.collect(ROOT, "unknown")
        result = subprocess.run(["bash", "scripts/format-code.sh"], cwd=ROOT, capture_output=True)
        self.assertEqual(result.returncode, 2)

    def test_changed_scope_includes_untracked_and_staged_sources(self):
        api = self.api()
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "-c", "user.name=test", "-c", "user.email=test@example.invalid", "commit", "--allow-empty", "-qm", "base"], cwd=self.root, check=True)
        file = self.root / "new.js"
        file.write_text("const x=1;")
        self.assertEqual(api.collect(self.root, "changed"), [file])
        subprocess.run(["git", "add", "new.js"], cwd=self.root, check=True)
        self.assertEqual(api.collect(self.root, "changed"), [file])


if __name__ == "__main__":
    unittest.main()

class AliasBoundaryTests(unittest.TestCase):
    def test_symlinked_parent_cannot_include_generated_or_external_files(self):
        from tools.bytedepth.formatting import collect
        from tools.bytedepth.process import ToolError
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as outside:
            root = Path(directory).resolve()
            (root / '.prettierignore').write_text('apps/mobile/android/\n')
            generated = root / 'apps/mobile/android/subtree'
            generated.mkdir(parents=True)
            (generated / 'config.json').write_text('{}')
            (root / 'alias').symlink_to(generated.parent, target_is_directory=True)
            self.assertEqual([], collect(root, 'paths', ['alias/subtree/config.json']))
            self.assertEqual([], collect(root, 'paths', ['alias/subtree']))
            external = Path(outside).resolve()
            (external / 'config.json').write_text('{}')
            (root / 'external').symlink_to(external, target_is_directory=True)
            try:
                selected = collect(root, 'paths', ['external/config.json'])
            except ToolError:
                selected = []
            self.assertEqual([], selected)
