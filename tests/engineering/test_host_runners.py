"""Signed Host transaction ordering and evidence gates, entirely offline."""
from contextlib import ExitStack
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from tools.bytedepth.process import ToolError
from tools.bytedepth.host import runners

SHA = 'a' * 40


class RunnerTests(unittest.TestCase):
    def scenario(self, failure=None):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        io = Mock()
        io.root = root
        io.env = {'ARTIFACT_DIGEST': 'sha256:' + 'b' * 64, 'RELEASE_PLATFORM_TASK_NONCE': 'nonce', 'RELEASE_PLATFORM_TEST_STATE_DIR': str(root / 'slots')}
        io.port.side_effect = lambda role: '18080' if role == 'app' else '18081'
        io.active.return_value = False
        io.call.return_value = ''
        manifest = root / 'slots/20260924_231530_a1b2c3d4/manifest'
        def provision(host, run, file):
            file.parent.mkdir(parents=True)
            file.write_text('bound resource identity')
            env = file.parent / 'staging-it.env'
            env.write_text('SPRING_PROFILES_ACTIVE=staging-it\n')
            return {'it_env': str(env)}
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.mocks = {}
        for name, value in [('runtime.candidate', None), ('runtime.ensure', None), ('resources.inputs', None), ('resources.provision', provision), ('resources.teardown', None), ('runtime.test_count', 3), ('runtime.private', None)]:
            kwargs = {'side_effect': value} if callable(value) else {'return_value': value}
            self.mocks[name] = self.stack.enter_context(patch('tools.bytedepth.host.runners.' + name, **kwargs))
        self.stack.enter_context(patch('tools.bytedepth.host.runners.new_run', return_value=manifest.parent.name))
        self.stack.enter_context(patch('tools.bytedepth.host.runners.runtime.directory', side_effect=lambda path, *args: Path(path).mkdir(parents=True, exist_ok=True) or Path(path)))
        self.stack.enter_context(patch('tools.bytedepth.host.runners.runtime.write', side_effect=lambda path, text: Path(path).write_text(text)))
        if failure == 'tests':
            io.stream.side_effect = ToolError('test process failed')
        elif failure == 'report':
            self.mocks['runtime.test_count'].side_effect = ToolError('zero tests')
        elif failure == 'cleanup':
            self.mocks['resources.teardown'].side_effect = ToolError('cleanup failed')
        elif failure == 'restore':
            io.wait.side_effect = ToolError('health failed')
        elif failure == 'candidate':
            self.mocks['runtime.candidate'].side_effect = [None, ToolError('candidate changed')]
        return io, root

    def test_success_evidence_binds_nonzero_tests_and_cleanup(self):
        io, root = self.scenario()
        runners.execute(io, SHA, 'integration', root / 'state', root / 'evidence', root / 'runtime')
        evidence = (root / 'evidence/staging-integration').read_text()
        for expected in ['commit=' + SHA, 'tests_total=3', 'artifact_digest=' + io.env['ARTIFACT_DIGEST'], 'attempt_nonce=nonce', 'cleanup=result=passed']:
            self.assertIn(expected, evidence)
        self.assertEqual(2, self.mocks['runtime.candidate'].call_count)
        io.control.assert_any_call('start', 'edge')
        self.assertEqual(['app', 'edge'], [call.args[0] for call in io.wait.call_args_list])
        args = io.stream.call_args.args[0]
        self.assertIn('-o', args)
        self.assertIn('-Pstaging-integration', args)

    def test_failures_never_leave_old_or_new_success_evidence(self):
        for failure in ['tests', 'report', 'cleanup', 'restore', 'candidate']:
            with self.subTest(failure=failure):
                io, root = self.scenario(failure)
                (root / 'evidence').mkdir()
                evidence = root / 'evidence/staging-integration'
                evidence.write_text('stale passing evidence')
                with self.assertRaises(ToolError):
                    runners.execute(io, SHA, 'integration', root / 'state', root / 'evidence', root / 'runtime')
                self.assertFalse(evidence.exists())
                io.control.assert_any_call('start', 'app')
                io.control.assert_any_call('start', 'edge')
                self.stack.close()

    def test_stopped_app_is_verified_before_provisioning(self):
        io, root = self.scenario()
        io.active.return_value = True
        with self.assertRaises(ToolError):
            runners.execute(io, SHA, 'integration', root / 'state', root / 'evidence', root / 'runtime')
        self.mocks['resources.provision'].assert_not_called()
        io.control.assert_any_call('start', 'edge')

    def e2e_scenario(self, failure=None):
        from urllib.parse import urlencode
        io, root = self.scenario()
        io.env.update(BYTEDEPTH_STAGING_E2E_USERNAME='account', BYTEDEPTH_STAGING_E2E_PASSWORD='private-password')
        jar = root / 'app.jar'
        jar.write_bytes(b'deployed jar')
        io.env['RELEASE_PLATFORM_TEST_JAR'] = str(jar)
        io.service.side_effect = lambda role: f'bytedepth-staging-native-{role}.service'
        query = {'client_id': 'bytedepth-staging', 'response_type': 'code', 'redirect_uri': runners.PUBLIC + '/login/oauth2/code/hearth', 'code_challenge_method': 'S256', 'code_challenge': 'a' * 43, 'scope': 'openid profile email', 'state': 'state', 'nonce': 'nonce'}
        authorize = 'https://staging-hearth.bytedepth.cn/oauth2/authorize?' + urlencode(query)
        def call(args, **kwargs):
            if args[-1] == runners.PUBLIC + '/login':
                return ('200' if failure == 'local-login' else '302') + '\n' + runners.PUBLIC + '/oauth2/authorization/hearth'
            if args[-1] == runners.PUBLIC + '/oauth2/authorization/hearth':
                return ('404' if failure == 'oidc' else '302') + '\n' + authorize
            if args[-1] == authorize:
                return ('403' if failure == 'hearth' else '302') + '\nhttps://staging-hearth.bytedepth.cn/login'
            if args[-1] == runners.PUBLIC + '/posts':
                return '<a href="/posts/fixture-post">post</a>'
            return ''
        io.call.side_effect = call
        original = self.mocks['resources.provision'].side_effect
        def provision(host, run, file):
            data = original(host, run, file)
            env = file.parent / 'staging-e2e.env'
            env.write_text('SPRING_PROFILES_ACTIVE=staging-e2e\n')
            data['e2e_env'] = str(env)
            return data
        self.mocks['resources.provision'].side_effect = provision
        self.stack.enter_context(patch.object(runners, 'SLOT_ENV', root / 'run/slot.env'))
        def stream(args, **kwargs):
            if failure == 'journal' and args[0] == 'journalctl':
                raise ToolError('unallowlisted warning in journal')
            if failure == 'playwright' and 'test:e2e' in args:
                raise ToolError('Playwright exited 17')
        io.stream.side_effect = stream
        if failure == 'playwright':
            self.stack.enter_context(patch.object(runners, 'diagnostics'))
        return io, root

    def test_e2e_runs_with_fixed_target_chromium_credentials_and_journal(self):
        io, root = self.e2e_scenario()
        runners.execute(io, SHA, 'e2e', root / 'state', root / 'evidence', root / 'runtime')
        self.assertTrue((root / 'evidence/staging-e2e').exists())
        self.assertFalse((root / 'run/slot.env').exists())
        self.assertEqual(runners.PUBLIC, io.env['E2E_BASE_URL'])
        self.assertEqual('fixture-post', io.env['E2E_POST_SLUG'])
        self.assertEqual('account', io.env['E2E_ADMIN_USERNAME'])
        self.assertEqual(str(runners.runtime.CHROMIUM), io.env['PLAYWRIGHT_CHROMIUM_EXECUTABLE'])
        journals = [call.args[0] for call in io.stream.call_args_list if call.args[0][0] == 'journalctl']
        self.assertEqual(1, len(journals))
        self.assertTrue(any(argument.startswith('--since=') for argument in journals[0]))
        logins = [call for call in io.call.call_args_list if call.args[0][-1] == runners.PUBLIC + '/login']
        self.assertEqual(2, len(logins))

    def test_e2e_failures_invalidate_evidence_including_deployed_sso_and_journal(self):
        for failure in ['local-login', 'oidc', 'hearth', 'journal', 'playwright']:
            with self.subTest(failure=failure):
                io, root = self.e2e_scenario(failure)
                (root / 'evidence').mkdir()
                file = root / 'evidence/staging-e2e'
                file.write_text('stale success')
                with self.assertRaises(ToolError):
                    runners.execute(io, SHA, 'e2e', root / 'state', root / 'evidence', root / 'runtime')
                self.assertFalse(file.exists())
                if failure in ('journal', 'playwright'):
                    io.control.assert_any_call('start', 'edge')
                self.stack.close()

    def test_failure_diagnostics_keep_metadata_and_exclude_task_payload_and_secrets(self):
        import io as streams
        from contextlib import redirect_stdout
        host = Mock()
        host.env = {'BYTEDEPTH_STAGING_E2E_SEARCH_INDEX': 'owned-index'}
        host.service.side_effect = lambda role: role + '.service'
        host.call.return_value = 'failed request http://127.0.0.1:17700/tasks/7295'
        host.http.side_effect = [{'status': 'available'}, {'results': [{'uid': 7384, 'indexUid': 'owned-index', 'status': 'failed', 'details': 'private document payload', 'error': {'message': 'secret bearer token', 'code': 'failed', 'type': 'internal'}}]}, {'uid': 7295, 'status': 'failed', 'details': 'private document payload'}]
        with redirect_stdout(streams.StringIO()) as output:
            runners.diagnostics(host, 'since')
        text = output.getvalue()
        self.assertIn('7384', text)
        self.assertIn('7295', text)
        self.assertIn('E2E_SEARCH_DIAGNOSTIC end', text)
        self.assertNotIn('private document payload', text)
        self.assertNotIn('secret bearer token', text)
        host.http.side_effect = ToolError('timeout')
        with redirect_stdout(streams.StringIO()) as output:
            runners.diagnostics(host, 'since')
        self.assertIn('E2E_SEARCH_DIAGNOSTIC end', output.getvalue())

    def test_default_slot_state_preserves_installed_host_path(self):
        io, root = self.scenario()
        io.env.pop('RELEASE_PLATFORM_TEST_STATE_DIR')
        requested = []
        def directory(path, *args):
            requested.append(Path(path))
            actual = root / 'slots' if Path(path) == Path('/var/lib/bytedepth-staging/test-slots') else Path(path)
            actual.mkdir(parents=True, exist_ok=True)
            return actual
        with patch.object(runners.runtime, 'directory', side_effect=directory):
            runners.execute(io, SHA, 'integration', root / 'state', root / 'evidence', root / 'runtime')
        self.assertIn(Path('/var/lib/bytedepth-staging/test-slots'), requested)

    def test_optional_metric_read_failure_does_not_replace_test_failure(self):
        import io as streams
        from contextlib import redirect_stdout
        host = Mock()
        host.service.side_effect = lambda role: role + '.service'
        with patch.object(Path, 'is_file', return_value=True), patch.object(Path, 'read_text', side_effect=OSError('disappeared')), redirect_stdout(streams.StringIO()) as output:
            runners.diagnostics(host, 'since')
        self.assertIn('E2E_SEARCH_DIAGNOSTIC end', output.getvalue())
