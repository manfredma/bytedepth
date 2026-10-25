"""在不连接真实服务的前提下验证资源隔离、SQL 策略和失败恢复。"""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

from tools.bytedepth.process import ToolError
from tools.bytedepth.host import resources

RUN = '20260924_231530_a1b2c3d4'
SAFE = "INSERT INTO article VALUES (1, 1.23);\nINSERT INTO category VALUES (1);\nINSERT INTO admin VALUES (1, '$argon2id$v=19$m=1$fixture');\n"


class ResourceTests(unittest.TestCase):
    def test_exact_resource_identity_rejects_staging_and_path_escape(self):
        for kind, name in [('db', 'bytedepth'), ('db', 'bytedepth_it_other'), ('user', 'staging'), ('index', 'posts'), ('namespace', 'bytedepth:'), ('directory', '/data/images')]:
            with self.subTest(kind=kind), self.assertRaises(ToolError):
                resources.scope(kind, name, RUN, Path('/data/images-test'))
        for kind, name in [('db', 'bytedepth_it_' + RUN), ('db', 'bytedepth_e2e_' + RUN), ('index', 'posts_it_' + RUN), ('namespace', 'bytedepth:it:' + RUN + ':')]:
            resources.scope(kind, name, RUN, Path('/data/images-test'))
        with self.assertRaises(ToolError):
            resources.run_id('../escape')
        with self.assertRaises(ToolError):
            resources.capacity('8', '0', '14', '15')
        with self.assertRaises(ToolError):
            resources.capacity('16', '14', '14', '15')
        resources.capacity('16', '0', '14', '15')

    def test_fixture_preserves_decimal_and_flyway_but_rejects_unsafe_sql(self):
        resources.fixture(SAFE + "INSERT INTO flyway_schema_history VALUES ('V1__init_tables.sql');")
        statements = ['USE bytedepth;', 'DELETE FROM article;', 'INSERT INTO `prod`.`article` VALUES (2);', 'INSERT INTO `prod`/**/.`article` VALUES (2);', 'system id;', '\\u', 'RENAME TABLE article TO copy;', 'RENAME USER a TO b;', 'SET GLOBAL max_connections = 100;', 'SET PERSIST_ONLY x = 1;', 'CALL dangerous_procedure();', 'LOCK TABLES article WRITE;', 'CREATE EVENT dangerous ON SCHEDULE EVERY 1 DAY DO DELETE FROM article;', 'LOAD XML LOCAL INFILE "x" INTO TABLE article;', 'PREPARE dangerous FROM "DELETE FROM article";', 'EXECUTE dangerous;', 'DEALLOCATE PREPARE dangerous;', '-- comment', '# comment', 'INSERT INTO article VALUES ("admin123");']
        for statement in statements:
            with self.subTest(statement=statement), self.assertRaises(ToolError):
                resources.fixture(SAFE + statement)
        with self.assertRaises(ToolError):
            resources.fixture('INSERT INTO article VALUES (1);')

    def test_grant_must_be_exactly_database_scoped(self):
        grant = f'GRANT ALL PRIVILEGES ON `db`.* TO `user`@`localhost`'
        usage = 'GRANT USAGE ON *.* TO `user`@`localhost`'
        resources.grants(grant + ';\n' + usage, 'db', 'user')
        for value in [usage, grant + '\n' + grant, grant + '\nGRANT ALL ON *.* TO `user`@`localhost`']:
            with self.assertRaises(ToolError):
                resources.grants(value, 'db', 'user')

    def test_baseline_allows_only_natural_expiry_with_tolerance(self):
        before = {'captured_at': 100, 'records': {'key': ['expiring', 'hash', 20000], 'index': ['persistent', 'hash', -1]}}
        resources.compare(before, {'captured_at': 105, 'records': {'key': ['expiring', 'hash', 15000], 'index': ['persistent', 'hash', -1]}})
        resources.compare(before, {'captured_at': 125, 'records': {'index': ['persistent', 'hash', -1]}})
        for current in [{'captured_at': 101, 'records': {'index': ['persistent', 'hash', -1]}}, {'captured_at': 101, 'records': {'key': ['expiring', 'changed', 19000], 'index': ['persistent', 'hash', -1]}}, {'captured_at': 125, 'records': {'index': ['persistent', 'changed', -1]}}, {'captured_at': 125, 'records': {'index': ['persistent', 'hash', -1], 'new': ['persistent', 'hash', -1]}}]:
            with self.assertRaises(ToolError):
                resources.compare(before, current)

    def test_redis_cleanup_rejects_out_of_scope_before_delete(self):
        io = Mock()
        io.env = {'BYTEDEPTH_TEST_IT_REDIS_DB': '14', 'BYTEDEPTH_TEST_E2E_REDIS_DB': '15'}
        io.port.return_value = '16379'
        io.call.return_value = 'bytedepth:foreign:key\n'
        with self.assertRaises(ToolError):
            resources.redis_delete(io, '14', 'bytedepth:it:' + RUN + ':', RUN)
        self.assertEqual(1, io.call.call_count)
        io.reset_mock()
        io.call.side_effect = ['bytedepth:it:' + RUN + ':sample\n', '1', '']
        resources.redis_delete(io, '14', 'bytedepth:it:' + RUN + ':', RUN)
        self.assertIn('DEL', io.call.call_args_list[1].args[0])
        self.assertNotIn('FLUSHDB', str(io.call.call_args_list))


class FakeHost:
    """有状态的边界替身：真实资源事务在模拟服务上运行。"""
    def __init__(self, root, failure=''):
        self.root = root
        self.native = {'BYTEDEPTH_NATIVE_ROOT': str(root)}
        self.env = {'BYTEDEPTH_TEST_STATE_DIR': str(root / 'slots'), 'BYTEDEPTH_TEST_CANDIDATE_SHA': 'a' * 40, 'BYTEDEPTH_TEST_STAGING_REDIS_DB': '0', 'BYTEDEPTH_TEST_IT_REDIS_DB': '14', 'BYTEDEPTH_TEST_E2E_REDIS_DB': '15'}
        self.calls = []
        self.failure = failure
        self.failed = False
        self.created = set()
        self.states = {'app': False, 'test-slot': False}
        for name, content in [('MYSQL_DEFAULTS_FILE', '[client]\n'), ('REDIS_SECRET_FILE', 'redis'), ('MEILI_SECRET_FILE', 'meili'), ('FIXTURE', SAFE)]:
            path = root / name
            path.write_text(content)
            self.env['BYTEDEPTH_TEST_' + name] = str(path)
        from tools.bytedepth.process import digest
        path = root / 'sha'
        path.write_text(digest(root / 'FIXTURE'))
        self.env['BYTEDEPTH_TEST_FIXTURE_SHA256_FILE'] = str(path)
        (root / 'slots').mkdir()
        (root / 'images-test').mkdir()

    def port(self, role):
        return {'app': '18080', 'mysql': '13306', 'redis': '16379', 'meili': '17700'}[role]

    def active(self, role):
        return self.states[role]

    def control(self, action, role):
        self.calls.append((action, role))
        if self.failure == 'stop-slot' and role == 'test-slot':
            raise ToolError('cannot stop')
        self.states[role] = action == 'start'

    def call(self, args, data=None, env=None):
        self.calls.append((args, data))
        if args[0] == 'redis-cli':
            return 'databases\n16' if 'CONFIG' in args else ''
        if 'SHOW GRANTS' in (data or ''):
            import re
            user = re.search("FOR '([^']+)'", data)[1]
            db = user.replace('bd_', 'bytedepth_', 1)
            return f'GRANT ALL PRIVILEGES ON `{db}`.* TO `{user}`@`localhost`'
        if 'CREATE DATABASE' in (data or ''):
            if self.failure == 'uncertain':
                self.failed = True
                raise ToolError('lost connection')
            self.created.add(data.split('`')[1])
        if 'CREATE USER' in (data or ''):
            if self.failure == 'user':
                raise ToolError('creation rejected')
            self.created.add(data.split("'")[1])
        if 'SELECT COUNT' in (data or ''):
            if self.failure == 'uncertain' and self.failed:
                raise ToolError('cannot query identity')
            return '1' if any("'" + name + "'" in data for name in self.created) else '0'
        if 'DROP DATABASE' in (data or ''):
            self.created.discard(data.split('`')[1])
        if 'DROP USER' in (data or ''):
            self.created.discard(data.split("'")[1])
        if 'SELECT DATABASE()' in args:
            return args[args.index('--batch') - 1]
        return ''

    def http(self, method, path, payload=None, missing=False):
        self.calls.append((method, path))
        if path == '/indexes/posts/settings':
            return {'searchableAttributes': [], 'filterableAttributes': [], 'sortableAttributes': []}
        if path == '/indexes/posts/stats':
            return {'numberOfDocuments': 0}
        if path.startswith('/tasks/'):
            return {'status': 'succeeded'}
        if path == '/keys' and method == 'POST':
            return {'key': 'scoped-key'}
        if path == '/indexes' and method == 'POST' or method == 'PATCH':
            return {'taskUid': 1}
        if method == 'DELETE':
            return {'taskUid': 1} if path.startswith('/indexes/') else None
        if path.startswith('/indexes/'):
            return None if missing else {'uid': path.rsplit('/', 1)[1]}
        raise AssertionError((method, path))


class TransactionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.private = patch.object(resources, 'private')
        self.private.start()
        self.addCleanup(self.private.stop)
        from contextlib import ExitStack
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(resources, 'directory', side_effect=lambda path: Path(path).mkdir(parents=True, exist_ok=True) or Path(path)))
        self.stack.enter_context(patch.object(resources, 'write', side_effect=lambda path, text: Path(path).write_text(text)))

    def test_complete_provision_and_teardown_preserve_staging(self):
        io = FakeHost(self.root)
        file = self.root / 'slots' / RUN / 'manifest'
        data = resources.provision(io, RUN, file)
        self.assertEqual(18, len(data))
        self.assertEqual(data, resources.manifest(io, file))
        self.assertNotIn('__PENDING_SCOPED_KEY__', Path(data['e2e_env']).read_text())
        self.assertNotIn('meili\n', Path(data['e2e_env']).read_text())
        io.states['test-slot'] = True
        resources.teardown(io, file)
        self.assertFalse(file.exists())
        self.assertEqual(set(), io.created)
        self.assertTrue(io.states['app'])
        stop = io.calls.index(('stop', 'test-slot'))
        first_delete = next(i for i, call in enumerate(io.calls) if call[0] == 'DELETE')
        self.assertLess(stop, first_delete)

    def test_uncertain_creation_preserves_every_resource_and_blocks_teardown(self):
        io = FakeHost(self.root, 'uncertain')
        file = self.root / 'slots' / RUN / 'manifest'
        with self.assertRaises(ToolError):
            resources.provision(io, RUN, file)
        self.assertTrue((file.parent / 'state-uncertain').is_file())
        count = len(io.calls)
        with self.assertRaises(ToolError):
            resources.teardown(io, file)
        self.assertEqual(count, len(io.calls))
        self.assertFalse(any(call[0] == 'DELETE' or 'DROP ' in str(call[1]) for call in io.calls))

    def test_confirmed_creation_failure_rolls_back_only_own_resources(self):
        io = FakeHost(self.root, 'user')
        file = self.root / 'slots' / RUN / 'manifest'
        with self.assertRaises(ToolError):
            resources.provision(io, RUN, file)
        self.assertFalse(file.parent.exists())
        self.assertEqual(set(), io.created)
        for call in io.calls:
            if 'DROP ' in str(call[1]):
                self.assertIn(RUN, call[1])

    def test_active_slot_stop_failure_prevents_destructive_cleanup(self):
        io = FakeHost(self.root)
        file = self.root / 'slots' / RUN / 'manifest'
        resources.provision(io, RUN, file)
        io.states['test-slot'] = True
        io.failure = 'stop-slot'
        count = len(io.calls)
        with self.assertRaises(ToolError):
            resources.teardown(io, file)
        self.assertTrue(file.exists())
        self.assertFalse(any(call[0] == 'DELETE' or 'DROP ' in str(call[1]) for call in io.calls[count:]))
