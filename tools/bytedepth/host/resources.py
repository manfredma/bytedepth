"""以同一事务管理严格限定范围的 staging MySQL、Redis、Meili 和图片资源。"""
import hashlib
import json
from pathlib import Path
import re
import secrets
import shutil
import time
import uuid

from ..process import ToolError, digest
from .runtime import PUBLIC, SHA, directory, private, require, values, write

RUN = re.compile(r'[0-9]{8}_[0-9]{6}_[a-z0-9]{8}')
RESOURCE_PREFIX = {'db': 'bytedepth_{profile}_{run}', 'user': 'bd_{profile}_{run}', 'index': 'posts_{profile}_{run}', 'namespace': 'bytedepth:{profile}:{run}:'}
REDIS_SNAPSHOT = '''local function hex(value)
local result = {}
for i = 1, #value do result[i] = string.format("%02x", string.byte(value, i)) end
return table.concat(result)
end
local cursor = "0"
local rows = {}
repeat
local reply = redis.call("SCAN", cursor)
cursor = reply[1]
for _, key in ipairs(reply[2]) do
 table.insert(rows, hex(key) .. "\\t" .. redis.call("PTTL", key) .. "\\t" .. hex(redis.call("DUMP", key) or ""))
end
until cursor == "0"
return rows'''


def run_id(value):
    require(RUN.fullmatch(value), 'Invalid RUN_ID')
    return value


def scope(kind, name, run, image_root):
    run_id(run)
    allowed = [str(Path(image_root) / run / profile) if kind == 'directory' else RESOURCE_PREFIX.get(kind, '').format(profile=profile, run=run) for profile in ('it', 'e2e')]
    require(name in allowed and name, 'Resource is outside RUN_ID scope')


def capacity(total, staging, it, e2e):
    require(all(re.fullmatch(r'[0-9]+', str(value)) for value in (total, staging, it, e2e)), 'Invalid Redis DB numbers')
    total, staging, it, e2e = map(int, (total, staging, it, e2e))
    require(total > max(it, e2e, staging) and it != e2e and staging not in (it, e2e), 'Insufficient Redis capacity or overlapping DBs')


def fixture(sql):
    for token in ('article', 'category', 'admin'):
        require(re.search(r'INSERT\s+INTO\s+[^\n]*' + token, sql, re.I), 'Fixture lacks required insert')
    require(re.search(r'\$2[aby]\$|\$argon2(id|i)\$', sql), 'Fixture lacks administrator password hash')
    sql = re.sub(r'[\n\r\t]', ' ', sql)
    forbidden = r'(^|[^a-z])(admin123|changeme|production|bytedepth\.cn)([^a-z]|$)|(--|#|/\*|\*/)|(^|[\s;])\\[a-z!#.]|(^|[\s;])(system|delimiter|pager|tee|source|use|connect|status|warnings|nowarning|charset|prompt|rehash|edit|go|print)([\s;]|$)|(^|[\s;])(USE|DELETE|UPDATE|DROP|ALTER|TRUNCATE|CREATE\s+(DATABASE|USER|EVENT|TRIGGER|PROCEDURE|FUNCTION|VIEW|ROLE|TABLESPACE|SERVER|LOGFILE)|GRANT|REVOKE|FLUSH|SOURCE|LOAD\s+(DATA|XML)|INTO\s+OUTFILE|RENAME(\s+(TABLE|USER))?|CALL|LOCK\s+TABLES|SET(\s+(GLOBAL|PERSIST|PERSIST_ONLY|SESSION))?|PREPARE|EXECUTE|DEALLOCATE)(\s|;|$)'
    require(not re.search(forbidden, sql, re.I), 'Fixture contains unsafe SQL or client commands')
    require(not re.search(r'(^|;)\s*(INSERT\s+INTO|CREATE\s+TABLE)\s+[^\s;]+\s*\.', sql, re.I), 'Fixture contains qualified database tables')


def grants(text, db, user):
    expected = f'GRANT ALL PRIVILEGES ON `{db}`.* TO `{user}`@`localhost`'
    usage = f'GRANT USAGE ON *.* TO `{user}`@`localhost`'
    lines = [line.rstrip(';') for line in text.splitlines() if line]
    require(lines.count(expected) == 1 and all(line in (expected, usage) for line in lines), 'Database grant is missing, duplicated or broader than the manifest database')


def redis(io, *args):
    return io.call(['redis-cli', '-h', '127.0.0.1', '-p', io.port('redis'), *args])


def mysql(io, sql):
    # SQL 通过标准输入传递，生成的密码不出现在进程参数中。
    return io.call(['mysql', '--defaults-extra-file=' + io.env['BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE'], '--host=127.0.0.1', '--port=' + io.port('mysql'), '--batch', '--skip-column-names'], data=sql)


def redis_delete(io, db, namespace, run):
    scope('namespace', namespace, run, '')
    require(db in (io.env.get('BYTEDEPTH_TEST_IT_REDIS_DB'), io.env.get('BYTEDEPTH_TEST_E2E_REDIS_DB')), 'Unreserved Redis DB')
    args = ['-n', db, '--scan', '--pattern', namespace + '*']
    keys = redis(io, *args).splitlines()
    require(all(key.startswith(namespace) for key in keys if key), 'Redis scan returned an out-of-scope key')
    for key in keys:
        if key:
            redis(io, '-n', db, 'DEL', key)
    require(not redis(io, *args).strip(), 'Redis cleanup left owned keys')


def wait_task(io, response):
    task = response.get('taskUid') if isinstance(response, dict) else None
    require(type(task) is int and task >= 0, 'Invalid Meili task identity')
    for _ in range(30):
        status = io.http('GET', f'/tasks/{task}').get('status')
        if status == 'succeeded':
            return
        require(status != 'failed', 'Meili task failed')
        time.sleep(1)
    raise ToolError('Meili task timeout')


def snapshot(io):
    db = io.env['BYTEDEPTH_TEST_STAGING_REDIS_DB']
    require(db.isdigit() and db not in (io.env['BYTEDEPTH_TEST_IT_REDIS_DB'], io.env['BYTEDEPTH_TEST_E2E_REDIS_DB']), 'Invalid staging Redis DB')
    records = {}
    for row in redis(io, '-n', db, '--raw', 'EVAL', REDIS_SNAPSHOT, '0').splitlines():
        if not row:
            continue
        parts = row.split('\t')
        require(len(parts) == 3, 'Invalid Redis snapshot row')
        key, ttl, dump = parts
        require(re.fullmatch(r'[0-9a-f]+', key) and re.fullmatch(r'[0-9a-f]+', dump) and re.fullmatch(r'-?[0-9]+', ttl), 'Invalid Redis snapshot response')
        ttl = int(ttl)
        if ttl == -2:
            continue
        require(ttl >= -1, 'Invalid Redis TTL')
        identity = 'redis:' + hashlib.sha256(key.encode()).hexdigest()
        records[identity] = ['persistent' if ttl == -1 else 'expiring', hashlib.sha256(dump.encode()).hexdigest(), ttl]
    stats = json.dumps(io.http('GET', '/indexes/posts/stats'), sort_keys=True, separators=(',', ':'))
    records['meili:index'] = ['persistent', hashlib.sha256(stats.encode()).hexdigest(), -1]
    return {'captured_at': int(time.time()), 'records': records}


def compare(before, current):
    elapsed = (current['captured_at'] - before['captured_at']) * 1000
    require(elapsed >= 0, 'Snapshot clock moved backwards')
    for key, (kind, value, ttl) in before['records'].items():
        actual = current['records'].get(key)
        if actual is None:
            require(kind == 'expiring' and ttl - elapsed <= 10000, 'Staging resource disappeared during test')
        else:
            require(actual[:2] == [kind, value], 'Staging resource changed during test')
            if kind == 'expiring':
                require(abs(actual[2] - (ttl - elapsed)) <= 10000, 'Staging TTL changed during test')
    require(not current['records'].keys() - before['records'].keys(), 'Unexpected staging resource created')


def inputs(io, fixture_required=True):
    env = io.env
    keys = ['MYSQL_DEFAULTS_FILE', 'REDIS_SECRET_FILE', 'MEILI_SECRET_FILE']
    if fixture_required:
        keys += ['FIXTURE', 'FIXTURE_SHA256_FILE']
    for key in keys:
        name = 'BYTEDEPTH_TEST_' + key
        require(env.get(name), 'Explicit credential and fixture files are required')
        private(env[name])
    for key in ('STAGING_REDIS_DB', 'IT_REDIS_DB', 'E2E_REDIS_DB'):
        require(env.get('BYTEDEPTH_TEST_' + key), 'Explicit Redis DB assignments required')
    require(env['BYTEDEPTH_TEST_IT_REDIS_DB'] == '14' and env['BYTEDEPTH_TEST_E2E_REDIS_DB'] == '15', 'Reserved Redis DBs must be 14 and 15')
    for name, key in [('REDISCLI_AUTH', 'REDIS_SECRET_FILE'), ('BYTEDEPTH_TEST_MEILI_API_KEY', 'MEILI_SECRET_FILE')]:
        value = Path(env['BYTEDEPTH_TEST_' + key]).read_text().rstrip('\n')
        require(value and '\n' not in value and '\r' not in value, 'Invalid service secret')
        env[name] = value
    local = 'http://127.0.0.1:' + io.port('meili')
    require(env.get('BYTEDEPTH_TEST_MEILI_URL', local) == local, 'Meili must use the local staging endpoint')
    env['BYTEDEPTH_TEST_MEILI_URL'] = local
    total = redis(io, 'CONFIG', 'GET', 'databases').strip().splitlines()[-1]
    capacity(total, env['BYTEDEPTH_TEST_STAGING_REDIS_DB'], env['BYTEDEPTH_TEST_IT_REDIS_DB'], env['BYTEDEPTH_TEST_E2E_REDIS_DB'])
    env['BYTEDEPTH_TEST_REDIS_CAPACITY'] = total


def image_root(io):
    return Path(io.native['BYTEDEPTH_NATIVE_ROOT']) / 'images-test'


def manifest(io, file):
    file = Path(file)
    private(file)
    private(file.parent, directory=True)
    data = values(file)
    run = run_id(data.get('run_id', ''))
    require(len(data) == 18 and SHA.fullmatch(data.get('candidate_sha', '')) and data.get('mode') == 'staging', 'Invalid manifest identity or fields')
    require(file == Path(io.env['BYTEDEPTH_TEST_STATE_DIR']) / run / 'manifest', 'Manifest path differs from RUN_ID')
    for profile, redis_db in [('it', '14'), ('e2e', '15')]:
        for kind in RESOURCE_PREFIX:
            value = data.get(profile + '_' + kind)
            require(value == RESOURCE_PREFIX[kind].format(profile=profile, run=run), 'Manifest resource differs from profile')
            scope(kind, value, run, image_root(io))
        require(re.fullmatch(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', data.get(profile + '_key_uid', '')), 'Invalid scoped key UID')
        require(data.get(profile + '_redis_db') == redis_db and data.get(profile + '_env') == str(file.parent / f'staging-{profile}.env'), 'Invalid reserved Redis DB or environment path')
    require(data.get('app_port') == io.port('app'), 'Invalid test app port')
    capacity(io.env['BYTEDEPTH_TEST_REDIS_CAPACITY'], io.env['BYTEDEPTH_TEST_STAGING_REDIS_DB'], data['it_redis_db'], data['e2e_redis_db'])
    return data


def profile_environment(io, data, profile, password):
    prefix = f'BYTEDEPTH_STAGING_{profile.upper()}_'
    fields = {'BYTEDEPTH_ENVIRONMENT': 'staging', 'BYTEDEPTH_DOMAIN': 'staging-bytedepth.bytedepth.cn', 'BYTEDEPTH_SITE_URL': PUBLIC, 'SERVER_PORT': io.port('app'), 'SPRING_PROFILES_ACTIVE': 'staging-' + profile}
    resource = {key: data[f'{profile}_{key}'] for key in ('db', 'user', 'index', 'namespace', 'redis_db')}
    settings = {'DATASOURCE_URL': f'jdbc:mysql://127.0.0.1:{io.port("mysql")}/{resource["db"]}', 'DATASOURCE_USERNAME': resource['user'], 'DATASOURCE_PASSWORD': password, 'REDIS_HOST': '127.0.0.1', 'REDIS_PORT': io.port('redis'), 'REDIS_DATABASE': resource['redis_db'], 'REDIS_PASSWORD': io.env['REDISCLI_AUTH'], 'REDIS_SESSION_NAMESPACE': resource['namespace'], 'REDIS_KEY_NAMESPACE': resource['namespace'], 'SEARCH_URL': io.env['BYTEDEPTH_TEST_MEILI_URL'], 'SEARCH_INDEX': resource['index'], 'SEARCH_API_KEY': '__PENDING_SCOPED_KEY__', 'UPLOAD_IMAGE_DIR': str(image_root(io) / data['run_id'] / profile)}
    fields.update({prefix + key: value for key, value in settings.items()})
    return fields


def write_environment(file, fields):
    require(all(re.fullmatch(r'[A-Z][A-Z0-9_]*', key) and value and '\n' not in value and '\r' not in value for key, value in fields.items()), 'Malformed generated profile environment')
    write(file, ''.join(f'{key}={value}\n' for key, value in fields.items()))


def provision(io, run, file):
    run_id(run)
    file = Path(file)
    state = Path(io.env['BYTEDEPTH_TEST_STATE_DIR'])
    private(state, directory=True)
    private(image_root(io), directory=True)
    require(file == state / run / 'manifest' and not file.parent.exists() and not file.parent.is_symlink(), 'Manifest must identify a fresh run')
    images = image_root(io) / run
    require(not images.exists() and not images.is_symlink(), 'Image run already exists')
    require(SHA.fullmatch(io.env.get('BYTEDEPTH_TEST_CANDIDATE_SHA', '')), 'Candidate SHA required')
    inputs(io)
    sql = Path(io.env['BYTEDEPTH_TEST_FIXTURE']).read_text()
    expected = ''.join(Path(io.env['BYTEDEPTH_TEST_FIXTURE_SHA256_FILE']).read_text().split())
    require(re.fullmatch(r'[0-9a-f]{64}', expected) and digest(Path(io.env['BYTEDEPTH_TEST_FIXTURE'])) == expected, 'Fixture checksum mismatch')
    fixture(sql)
    data = {'run_id': run, 'candidate_sha': io.env['BYTEDEPTH_TEST_CANDIDATE_SHA'], 'mode': 'staging', 'app_port': io.port('app')}
    for profile, db in [('it', '14'), ('e2e', '15')]:
        data.update({f'{profile}_{kind}': pattern.format(profile=profile, run=run) for kind, pattern in RESOURCE_PREFIX.items()})
        data.update({f'{profile}_redis_db': db, f'{profile}_key_uid': str(uuid.uuid4()), f'{profile}_env': str(file.parent / f'staging-{profile}.env')})
        require(mysql(io, f"SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='{data[profile + '_db']}'").strip() == '0', 'Run database already exists')
        require(mysql(io, f"SELECT COUNT(*) FROM mysql.user WHERE user='{data[profile + '_user']}' AND host='localhost'").strip() == '0', 'Run user already exists')
        require(io.http('GET', '/indexes/' + data[profile + '_index'], missing=True) is None, 'Run index already exists')
    require(not io.active('app'), 'Staging app must be stopped before provisioning')
    created = []
    uncertain = False
    local_created = []
    try:
        for path in (file.parent, images):
            directory(path)
            local_created.append(path)
        write(file.parent / 'staging-baseline', json.dumps(snapshot(io)))
        passwords = {profile: secrets.token_hex(24) for profile in ('it', 'e2e')}
        environments = {}
        for profile in ('it', 'e2e'):
            directory(images / profile)
            environments[profile] = profile_environment(io, data, profile, passwords[profile])
            write_environment(data[profile + '_env'], environments[profile])
        write(file, ''.join(f'{key}={value}\n' for key, value in data.items()))
        manifest(io, file)
        settings = io.http('GET', '/indexes/posts/settings')
        require(all(isinstance(settings.get(key), list) for key in ('searchableAttributes', 'filterableAttributes', 'sortableAttributes')), 'Staging index settings unavailable')
        for profile in ('it', 'e2e'):
            uid, index = data[profile + '_key_uid'], data[profile + '_index']
            created.append(('key', uid))
            try:
                response = io.http('POST', '/keys', {'uid': uid, 'name': 'bytedepth staging test slot', 'actions': ['*'], 'indexes': [index], 'expiresAt': None})
            except (ToolError, OSError):
                response = io.http('GET', '/keys/' + uid)
            key = response.get('key', '')
            require(re.fullmatch(r'[A-Za-z0-9_-]+', key), 'Invalid scoped Meili key')
            environments[profile][f'BYTEDEPTH_STAGING_{profile.upper()}_SEARCH_API_KEY'] = key
            write_environment(data[profile + '_env'], environments[profile])
        for profile in ('it', 'e2e'):
            db, user, index = (data[profile + '_' + kind] for kind in ('db', 'user', 'index'))
            for kind, name, statement, query in [('db', db, f'CREATE DATABASE `{db}`', f"SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='{db}'"), ('user', user, f"CREATE USER '{user}'@'localhost' IDENTIFIED BY '{passwords[profile]}'", f"SELECT COUNT(*) FROM mysql.user WHERE user='{user}' AND host='localhost'")]:
                created.append((kind, name))
                try:
                    mysql(io, statement)
                except (ToolError, OSError):
                    try:
                        count = mysql(io, query).strip()
                    except (ToolError, OSError):
                        uncertain = True
                        raise ToolError('Creation failed and resource identity is unknown') from None
                    require(count == '1', 'Resource creation was not confirmed')
            mysql(io, f"GRANT ALL PRIVILEGES ON `{db}`.* TO '{user}'@'localhost'")
            grants(mysql(io, f"SHOW GRANTS FOR '{user}'@'localhost'"), db, user)
            user_args = ['mysql', '-u', user, '-h', '127.0.0.1', '--port=' + io.port('mysql'), db]
            environment = dict(io.env, MYSQL_PWD=passwords[profile])
            io.call(user_args, data=sql, env=environment)
            require(io.call(user_args + ['--batch', '--skip-column-names', '-e', 'SELECT DATABASE()'], env=environment).strip() == db, 'MySQL selected an unexpected database')
            created.append(('index', index))
            try:
                task = io.http('POST', '/indexes', {'uid': index, 'primaryKey': 'id'})
            except (ToolError, OSError):
                require(io.http('GET', '/indexes/' + index).get('uid') == index, 'Meili index creation identity is unknown')
            else:
                wait_task(io, task)
            wait_task(io, io.http('PATCH', '/indexes/' + index + '/settings', settings))
            require(io.http('GET', '/indexes/' + index).get('uid') == index, 'Created index identity differs')
        return data
    except BaseException as error:
        if uncertain or not isinstance(error, Exception):
            if file.parent in local_created:
                write(file.parent / 'state-uncertain', 'state_uncertain=1\n')
            raise
        failures = []
        for kind, name in reversed(created):
            try:
                delete(io, kind, name)
            except Exception as cleanup_error:
                failures.append(cleanup_error)
        if failures:
            raise ToolError('Partial provision cleanup failed; state retained') from error
        for path in reversed(local_created):
            require(not path.is_symlink(), 'Local cleanup target was replaced by symlink')
            shutil.rmtree(path)
        raise


def delete(io, kind, name):
    if kind in ('index', 'key'):
        response = io.http('DELETE', ('/indexes/' if kind == 'index' else '/keys/') + name, missing=True)
        if kind == 'index' and response is not None:
            wait_task(io, response)
    elif kind == 'user':
        mysql(io, f"DROP USER IF EXISTS '{name}'@'localhost'")
    elif kind == 'db':
        mysql(io, f'DROP DATABASE IF EXISTS `{name}`')
        require(mysql(io, f"SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='{name}'").strip() == '0', 'Dropped database still exists')


def teardown(io, file):
    file = Path(file)
    private(Path(io.env['BYTEDEPTH_TEST_STATE_DIR']), directory=True)
    private(image_root(io), directory=True)
    data = manifest(io, file)
    require(not (file.parent / 'state-uncertain').exists(), 'Resource state uncertain; refusing destructive cleanup until manual recovery')
    baseline = file.parent / 'staging-baseline'
    private(baseline)
    for profile in ('it', 'e2e'):
        private(data[profile + '_env'])
    inputs(io, fixture_required=False)
    if io.active('test-slot'):
        io.control('stop', 'test-slot')
        require(not io.active('test-slot'), 'Test slot could not be stopped; refusing destructive cleanup')
    failures = []
    for profile in ('it', 'e2e'):
        try:
            redis_delete(io, data[profile + '_redis_db'], data[profile + '_namespace'], data['run_id'])
        except Exception as error:
            failures.append(error)
        for kind in ('index', 'key', 'user', 'db'):
            try:
                delete(io, kind, data[profile + ('_key_uid' if kind == 'key' else '_' + kind)])
            except Exception as error:
                failures.append(error)
        path = image_root(io) / data['run_id'] / profile
        try:
            scope('directory', str(path), data['run_id'], image_root(io))
            require(not path.is_symlink(), 'Image cleanup target is a symlink')
            if path.is_dir():
                shutil.rmtree(path)
        except Exception as error:
            failures.append(error)
    try:
        compare(json.loads(baseline.read_text()), snapshot(io))
        io.control('start', 'app')
        require(io.active('app'), 'Staging app did not restart')
    except Exception as error:
        failures.append(error)
    require(not failures, 'Cleanup or staging restoration failed; preserving manifest')
    for profile in ('it', 'e2e'):
        Path(data[profile + '_env']).unlink()
    file.unlink()
    baseline.unlink()
    file.parent.rmdir()
    (image_root(io) / data['run_id']).rmdir()
