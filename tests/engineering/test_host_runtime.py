"""Host invariants are exercised without root, network, services or deployment."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.bytedepth.process import ToolError
from tools.bytedepth.host import runtime


class RuntimeTests(unittest.TestCase):
    def test_maven_inputs_follow_reactor_without_git_and_bind_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'pom.xml').write_text('<project><modules><module>a</module></modules></project>')
            (root / 'a').mkdir()
            (root / 'a/pom.xml').write_text('<project/>')
            original = runtime.maven_inputs(root)
            (root / 'target').mkdir()
            (root / 'target/pom.xml').write_text('ignored generated content')
            self.assertEqual(original, runtime.maven_inputs(root))
            (root / 'a/pom.xml').write_text('<project>changed</project>')
            self.assertNotEqual(original, runtime.maven_inputs(root))
            (root / 'pom.xml').write_text('<project><modules><module>missing</module></modules></project>')
            with self.assertRaises((ToolError, OSError)):
                runtime.maven_inputs(root)

    def test_config_is_literal_private_and_cannot_replace_signed_context(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / 'config'
            file.write_text('BYTEDEPTH_TEST_FIXTURE=/safe/a b.sql\n')
            with patch.object(runtime, 'private'):
                self.assertEqual('/safe/a b.sql', runtime.test_configuration({'BYTEDEPTH_STAGING_TEST_CONFIG': str(file)})['BYTEDEPTH_TEST_FIXTURE'])
                file.write_text('ARTIFACT_DIGEST=forged\n')
                with self.assertRaises(ToolError):
                    runtime.test_configuration({'BYTEDEPTH_STAGING_TEST_CONFIG': str(file)})
            with self.assertRaises(ToolError):
                runtime.test_configuration({'E2E_ADMIN_USERNAME': 'incomplete'})

    def test_candidate_binds_all_signed_inputs_and_rejects_duplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sha = 'a' * 40
            artifact = 'sha256:' + 'b' * 64
            (root / '.release-platform-source-commit').write_text(sha)
            archive = root / 'source.tar.gz'
            archive.write_bytes(b'archive')
            manifest = root / 'release'
            contents = f'commit={sha}\nsource_commit={sha}\nartifact_digest={artifact}\nsource_sha256={runtime.digest(archive)}\n'
            manifest.write_text(contents)
            history = root / 'history'
            history.write_text(f'commit={sha}\nartifact_digest={artifact}\n')
            env = {'ARTIFACT_DIGEST': artifact, 'RELEASE_PLATFORM_TASK_NONCE': 'attempt', 'RELEASE_PLATFORM_TEST_RELEASE_MANIFEST': str(manifest), 'RELEASE_PLATFORM_TEST_SOURCE_ARCHIVE': str(archive), 'RELEASE_PLATFORM_TEST_DEPLOY_HISTORY': str(history)}
            runtime.candidate(root, sha, env)
            for key, value in [('RELEASE_PLATFORM_TASK_NONCE', ''), ('ARTIFACT_DIGEST', 'sha256:' + 'c' * 64), ('RELEASE_PLATFORM_TEST_ARTIFACT_DIGEST', 'different')]:
                with self.subTest(key=key), self.assertRaises(ToolError):
                    runtime.candidate(root, sha, dict(env, **{key: value}))
            manifest.write_text(contents + f'commit={sha}\n')
            with self.assertRaises(ToolError):
                runtime.candidate(root, sha, env)

    def test_reports_require_real_successful_cases(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / 'playwright.json'
            for stats, valid in [({'expected': 2, 'unexpected': 0, 'flaky': 0}, True), ({'expected': 0, 'unexpected': 0}, False), ({'expected': 2, 'unexpected': 1}, False), ({'expected': 2, 'unexpected': 0, 'flaky': 1}, False), ({'expected': True, 'unexpected': 0}, False)]:
                report.write_text(json.dumps({'stats': stats}))
                if valid:
                    self.assertEqual(2, runtime.test_count('e2e', report))
                else:
                    with self.assertRaises((ToolError, ValueError)):
                        runtime.test_count('e2e', report)
            report = root / 'a/target/failsafe-reports/failsafe-summary.xml'
            report.parent.mkdir(parents=True)
            report.write_text('<failsafe-summary><completed>3</completed><skipped>1</skipped><errors>0</errors><failures>0</failures></failsafe-summary>')
            self.assertEqual(2, runtime.test_count('integration', root))
            report.write_text(report.read_text().replace('<completed>3</completed>', '<completed>3</completed><completed>3</completed>'))
            with self.assertRaises(ToolError):
                runtime.test_count('integration', root)

    def test_hearth_validation_never_accepts_local_login_or_foreign_client(self):
        from urllib.parse import urlencode
        query = {'client_id': 'bytedepth-staging', 'response_type': 'code', 'redirect_uri': runtime.PUBLIC + '/login/oauth2/code/hearth', 'code_challenge_method': 'S256', 'code_challenge': 'x' * 43, 'scope': 'openid profile', 'state': 'state', 'nonce': 'nonce'}
        url = 'https://staging-hearth.bytedepth.cn/oauth2/authorize?' + urlencode(query)
        self.assertEqual(url, runtime.hearth_authorization('302\n' + url))
        for key, value in [('client_id', 'production'), ('code_challenge_method', 'plain'), ('nonce', ''), ('code_challenge', 'short'), ('scope', 'profile')]:
            with self.subTest(key=key), self.assertRaises(ToolError):
                runtime.hearth_authorization('302\nhttps://staging-hearth.bytedepth.cn/oauth2/authorize?' + urlencode(dict(query, **{key: value})))
        with self.assertRaises(ToolError):
            runtime.hearth_authorization('200\n' + url)

    def test_runtime_refresh_stamps_only_successful_complete_inputs(self):
        from unittest.mock import Mock
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            io = Mock(root=root, env={'BYTEDEPTH_TEST_CANDIDATE_SHA': 'a' * 40})
            io.root = root
            manifest = root / 'manifest'
            facts = {'package_lock_sha256': 'lock', 'maven_inputs_sha256': 'maven', 'chromium_version': 'chrome', 'playwright_ffmpeg_version': 'ffmpeg'}
            with patch.object(runtime, 'digest', return_value='lock'), patch.object(runtime, 'maven_inputs', return_value='maven'), patch.object(runtime, 'facts', return_value=facts), patch.object(runtime, 'MAVEN', root), patch.object(runtime, 'directory'), patch.object(runtime, 'lock', return_value=__import__('contextlib').nullcontext()), patch.object(runtime, 'write', side_effect=lambda file, data: Path(file).write_text(data)), patch.object(runtime, 'Timing'):
                runtime.ensure(io, manifest)
                self.assertEqual(facts, runtime.values(manifest))
                calls = io.stream.call_count
                runtime.ensure(io, manifest)
                self.assertEqual(calls, io.stream.call_count)
                manifest.write_text('maven_inputs_sha256=stale\n')
                io.stream.side_effect = ToolError('offline probe failed')
                with self.assertRaises(ToolError):
                    runtime.ensure(io, manifest)
                self.assertEqual('maven_inputs_sha256=stale\n', manifest.read_text())

    def test_timing_identity_phase_and_interval_are_checked(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / 'timing'
            with patch.object(runtime, 'directory'), patch.object(runtime, 'write', side_effect=lambda path, data: Path(path).write_text(data)):
                timing = runtime.Timing(file, 'candidate')
                timing.record('phase', 'passed', 10, 21)
                self.assertIn('duration_ms=11', file.read_text())
                for args in [('bad phase', 'passed', 10, 21), ('phase', 'unknown', 10, 21), ('phase', 'passed', 21, 10)]:
                    with self.assertRaises(ToolError):
                        timing.record(*args)
                file.write_text('identity=other\n')
                with self.assertRaises(ToolError):
                    timing.record('phase', 'passed', 10, 21)

    def test_maven_all_configuration_wrapper_profile_paths_and_relocation(self):
        import shutil
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'source'
            root.mkdir()
            (root / 'pom.xml').write_text('<project xmlns="urn:maven"><build><configuration><modules><module>ignore</module></modules></configuration></build><profiles><profile><modules><module>nested</module></modules></profile></profiles></project>')
            (root / 'nested').mkdir()
            (root / 'nested/pom.xml').write_text('<project/>')
            old = runtime.maven_inputs(root)
            for name in ['.mvn/settings.xml', '.mvn/maven.config', '.mvn/jvm.config', '.mvn/extensions.xml', '.mvn/wrapper/maven-wrapper.properties', '.mvn/wrapper/maven-wrapper.jar', 'mvnw', 'mvnw.cmd']:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('initial')
                new = runtime.maven_inputs(root)
                self.assertNotEqual(old, new, name)
                path.write_text('changed')
                self.assertNotEqual(new, runtime.maven_inputs(root), name)
                old = runtime.maven_inputs(root)
            path = root / '.mvn/extensions.xml'
            path.rename(path.with_name('renamed.xml'))
            self.assertNotEqual(old, runtime.maven_inputs(root))
            old = runtime.maven_inputs(root)
            path.with_name('renamed.xml').unlink()
            self.assertNotEqual(old, runtime.maven_inputs(root))
            clone = root.parent / 'relocated'
            shutil.copytree(root, clone)
            self.assertEqual(runtime.maven_inputs(root), runtime.maven_inputs(clone))
            (root / 'pom.xml').write_text('<project><modules><module>../relocated</module></modules></project>')
            with self.assertRaises(ValueError):
                runtime.maven_inputs(root)

    def test_private_file_rejects_public_mode_and_symlink(self):
        import os
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / 'secret'
            file.write_text('secret')
            with patch.object(runtime.pwd, 'getpwnam', return_value=SimpleNamespace(pw_uid=os.getuid())):
                file.chmod(0o600)
                runtime.private(file)
                file.chmod(0o644)
                with self.assertRaises(ToolError):
                    runtime.private(file)
                link = file.with_name('link')
                link.symlink_to(file)
                with self.assertRaises(ToolError):
                    runtime.private(link)

    def test_native_configuration_preserves_trusted_shell_grammar(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / 'native.conf'
            file.write_text('BYTEDEPTH_NATIVE_STACK_MODE=parallel\nBYTEDEPTH_NATIVE_ROOT="/data/bytedepth-native-staging"\nbase=18080\nBYTEDEPTH_NATIVE_APP_PORT=$base\nBYTEDEPTH_NATIVE_MYSQL_PORT=13306\nBYTEDEPTH_NATIVE_REDIS_PORT=16379\nBYTEDEPTH_NATIVE_MEILI_PORT=17700\nBYTEDEPTH_NATIVE_EDGE_PORT=$((base + 1))\n')
            io = runtime.Host(root, {'PATH': __import__('os').environ['PATH'], 'BYTEDEPTH_STAGING_NATIVE_CONFIG': str(file)})
            self.assertEqual('18081', io.port('edge'))
            file.write_text(file.read_text().replace('parallel', 'production'))
            with self.assertRaises(ToolError):
                runtime.Host(root, io.env)

    def test_cli_bad_arguments_fail_before_any_host_operation(self):
        from tools.bytedepth.host.cli import main
        for args in [['integration'], ['e2e', '--unknown'], ['integration', '--commit-sha', 'short'], ['e2e', '--commit-sha', 'a' * 40, '--commit-sha', 'b' * 40]]:
            with self.subTest(args=args), patch.object(runtime, 'Host') as host:
                with self.assertRaises((SystemExit, ToolError)):
                    main(args)
                host.assert_not_called()

    def test_shared_lock_never_changes_existing_parent_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            parent.chmod(0o755)
            original = parent.stat()
            with patch.object(runtime, 'owned') as chown:
                with runtime.lock(parent / 'shared.lock'):
                    self.assertEqual(0o755, parent.stat().st_mode & 0o777)
                    self.assertEqual(original.st_uid, parent.stat().st_uid)
                chown.assert_not_called()
