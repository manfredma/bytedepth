"""Immutable candidate, installed configuration and dependency runtime contracts."""
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import subprocess
import stat
import time
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

from ..process import ToolError, atomic_write, digest, run, warning_lines

PUBLIC = 'https://staging-bytedepth.bytedepth.cn'
CHROMIUM = Path('/opt/shared-e2e/chrome-linux64/chrome')
BROWSERS = Path('/root/.cache/ms-playwright')
MAVEN = Path('/opt/shared-maven/repository')
SHA = re.compile(r'[0-9a-f]{40}')
TEST_KEYS = frozenset('BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE BYTEDEPTH_TEST_REDIS_SECRET_FILE BYTEDEPTH_TEST_MEILI_SECRET_FILE BYTEDEPTH_TEST_FIXTURE BYTEDEPTH_TEST_FIXTURE_SHA256_FILE BYTEDEPTH_TEST_STAGING_REDIS_DB BYTEDEPTH_TEST_IT_REDIS_DB BYTEDEPTH_TEST_E2E_REDIS_DB BYTEDEPTH_STAGING_E2E_USERNAME BYTEDEPTH_STAGING_E2E_PASSWORD_FILE'.split())


def require(condition, message):
    if not condition:
        raise ToolError(message)


def values(path):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), 'Missing or symlinked identity/configuration file')
    result = {}
    for line in path.read_text().splitlines():
        if not line or line.startswith('#'):
            continue
        key, separator, value = line.partition('=')
        require(separator and key not in result and value, 'Malformed or duplicate configuration field')
        result[key] = value
    return result


def private(path, directory=False):
    path = Path(path)
    require(not path.is_symlink() and (path.is_dir() if directory else path.is_file()), 'Missing or symlinked private path')
    info = path.stat()
    require(info.st_uid == pwd.getpwnam('ubuntu').pw_uid and info.st_mode & 0o777 == (0o700 if directory else 0o600), 'Private path must be ubuntu-owned with mode 0700/0600')
    if directory:
        require(os.access(path, os.W_OK), 'Private directory is not writable')


def owned(path):
    account = pwd.getpwnam('ubuntu')
    os.chown(path, account.pw_uid, account.pw_gid)


def directory(path, mode=0o700):
    path = Path(path)
    require(not path.is_symlink(), 'Directory cannot be a symlink')
    path.mkdir(parents=True, exist_ok=True, mode=mode)
    os.chmod(path, mode)
    owned(path)
    return path


def write(path, data):
    atomic_write(Path(path), data, mode=0o600)
    owned(path)


def test_configuration(environment):
    env = dict(environment)
    if env.get('E2E_ADMIN_USERNAME') or env.get('E2E_ADMIN_PASSWORD'):
        require(env.get('E2E_ADMIN_USERNAME') and env.get('E2E_ADMIN_PASSWORD'), 'Shared E2E account is incomplete')
        env.update(BYTEDEPTH_STAGING_E2E_USERNAME=env['E2E_ADMIN_USERNAME'], BYTEDEPTH_STAGING_E2E_PASSWORD=env['E2E_ADMIN_PASSWORD'])
    config = Path(env.get('BYTEDEPTH_STAGING_TEST_CONFIG', '/etc/bytedepth/staging-test.env'))
    if config.exists() or config.is_symlink():
        private(config)
        for key, value in values(config).items():
            require(key in TEST_KEYS, 'Unsupported staging test configuration key')
            if not env.get(key):
                env[key] = value
        password_file = env.get('BYTEDEPTH_STAGING_E2E_PASSWORD_FILE')
        if password_file and not env.get('BYTEDEPTH_STAGING_E2E_PASSWORD'):
            private(password_file)
            env['BYTEDEPTH_STAGING_E2E_PASSWORD'] = Path(password_file).read_text().rstrip('\n')
            require(env['BYTEDEPTH_STAGING_E2E_PASSWORD'], 'Empty E2E password')
    return env


def native_configuration(io):
    # Installed native configuration is trusted Bash, not dotenv. Execute exactly
    # that grammar and export only the established native fields, never eval data.
    names = ['BYTEDEPTH_NATIVE_' + name for name in ('STACK_MODE', 'ROOT', 'APP_PORT', 'MYSQL_PORT', 'REDIS_PORT', 'MEILI_PORT', 'EDGE_PORT')]
    config = io.env.get('BYTEDEPTH_STAGING_NATIVE_CONFIG', '/etc/bytedepth/staging-native.conf')
    require(Path(config).is_file(), 'Staging native configuration is missing')
    output = io.call(['bash', '-eu', '-c', 'source "$1"; shift; for key do printf "%s\\0" "${!key-}"; done', 'native-config', config, *names])
    fields = output.split('\0')
    require(len(fields) == len(names) + 1 and fields[-1] == '', 'Malformed native configuration output')
    result = dict(zip(names, fields[:-1]))
    require(result[names[0]] == 'parallel' and result[names[1]] == '/data/bytedepth-native-staging', 'Staging must use the isolated parallel native target')
    for name in names[2:]:
        require(result[name].isdigit() and 0 < int(result[name]) < 65536, 'Invalid native port')
    return result


def candidate(root, sha, env):
    require(SHA.fullmatch(sha), 'A full candidate SHA is required')
    artifact = env.get('ARTIFACT_DIGEST', '')
    require(re.fullmatch(r'sha256:[0-9a-f]{64}', artifact) and env.get('RELEASE_PLATFORM_TASK_NONCE'), 'Signed digest and attempt nonce are required')
    require(not env.get('RELEASE_PLATFORM_TEST_ARTIFACT_DIGEST') or env['RELEASE_PLATFORM_TEST_ARTIFACT_DIGEST'] == artifact, 'Verifier digest differs from signed task')
    marker = Path(root) / '.release-platform-source-commit'
    require(marker.is_file() and not marker.is_symlink() and marker.read_text().strip() == sha, 'Source snapshot does not match candidate')
    manifest_path = Path(env['RELEASE_PLATFORM_TEST_RELEASE_MANIFEST'])
    manifest = values(manifest_path)
    history = values(env.get('RELEASE_PLATFORM_TEST_DEPLOY_HISTORY', '/var/lib/bytedepth-staging/deploy-history'))
    archive = Path(env.get('RELEASE_PLATFORM_TEST_SOURCE_ARCHIVE', str(manifest_path.parent / 'staging-source.tar.gz')))
    require(archive.is_file() and not archive.is_symlink(), 'Missing source archive')
    require(manifest.get('commit') == sha and manifest.get('source_commit') == sha and history.get('commit') == sha and manifest.get('artifact_digest') == artifact and history.get('artifact_digest') == artifact and manifest.get('source_sha256') == digest(archive), 'Source, deployed artifact or archive does not match signed candidate')


def maven_inputs(root):
    root = Path(root).resolve()
    inputs = set()

    def visit(path):
        path = path.resolve()
        path.relative_to(root)
        if path in inputs:
            return
        inputs.add(path)
        project = ET.parse(path).getroot()
        for module in project.findall('./{*}modules/{*}module') + project.findall('./{*}profiles/{*}profile/{*}modules/{*}module'):
            require(module.text and module.text.strip(), 'Empty reactor module')
            child = path.parent / module.text.strip()
            visit(child if child.is_file() else child / 'pom.xml')

    visit(root / 'pom.xml')
    inputs.update(path.resolve() for path in (root / '.mvn').rglob('*') if path.is_file())
    inputs.update(root / name for name in ('mvnw', 'mvnw.cmd') if (root / name).is_file())
    result = hashlib.sha256(b'bytedepth-maven-inputs-v1\0')
    for path in sorted(inputs, key=lambda item: item.relative_to(root).as_posix()):
        for part in (path.relative_to(root).as_posix().encode(), path.read_bytes()):
            result.update(len(part).to_bytes(8, 'big'))
            result.update(part)
    return result.hexdigest()


def test_count(phase, path):
    def integer(value):
        require(type(value) is int and 0 <= value <= 2**53 - 1, 'Invalid test count')
        return value
    count = failed = 0
    if phase == 'integration':
        for file in Path(path).glob('**/target/failsafe-reports/failsafe-summary.xml'):
            tree = ET.parse(file).getroot()
            def field(name):
                entries = tree.findall(name)
                require(len(entries) == 1 and re.fullmatch(r'\s*[0-9]+\s*', entries[0].text or ''), 'Missing or duplicate test report field')
                return integer(int(entries[0].text))
            completed, skipped = field('completed'), field('skipped')
            require(skipped <= completed, 'Skipped count exceeds completed count')
            count += completed - skipped
            failed += field('errors') + field('failures')
    else:
        stats = json.loads(Path(path).read_text())['stats']
        count = integer(stats['expected'])
        failed = integer(stats['unexpected']) + integer(stats.get('flaky', 0))
    require(count > 0 and failed == 0, 'No successful test cases or failing test report')
    return count


def hearth_authorization(response):
    status, separator, location = response.partition('\n')
    url = urlsplit(location)
    query = parse_qs(url.query)
    required = {'client_id': ['bytedepth-staging'], 'response_type': ['code'], 'redirect_uri': [PUBLIC + '/login/oauth2/code/hearth'], 'code_challenge_method': ['S256']}
    require(separator and status == '302' and url.scheme == 'https' and url.netloc == 'staging-hearth.bytedepth.cn' and url.path == '/oauth2/authorize' and not url.fragment and all(query.get(key) == value for key, value in required.items()) and len(query.get('scope', [])) == 1 and 'openid' in query['scope'][0].split() and len(query.get('state', [])) == 1 and len(query.get('nonce', [])) == 1 and len(query.get('code_challenge', [])) == 1 and re.fullmatch(r'[A-Za-z0-9_-]{43}', query['code_challenge'][0]), 'Deployed Hearth entry must use the staging client, state, nonce and PKCE S256')
    return location


class Host:
    """Single subprocess/HTTP boundary; tests replace this object, never sudo."""
    def __init__(self, root, env, native=True):
        self.root, self.env = Path(root), dict(env)
        self.native = native_configuration(self) if native else {}

    def call(self, args, data=None, env=None):
        result = subprocess.run(args, cwd=self.root, env=self.env if env is None else env, input=data, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        # Secrets (SQL/passwords, bearer headers, redirect state) are never printed
        # from this boundary. Callers report the failing operation, not its args.
        require(result.returncode == 0, f'Host command failed: {Path(args[0]).name}')
        require(not warning_lines(result.stderr), f'Host command emitted an unallowlisted warning: {Path(args[0]).name}')
        return result.stdout

    def stream(self, args, log=None):
        secrets = [value for key, value in self.env.items() if any(token in key for token in ('PASSWORD', 'SECRET', 'API_KEY', 'REDISCLI_AUTH')) and value]
        run(args, self.root, env=self.env, secrets=secrets, warnings=True, log=log)

    def service(self, role):
        return f'bytedepth-staging-native-{role}.service'

    def active(self, role):
        result = subprocess.run(['systemctl', 'is-active', '--quiet', self.service(role)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        require(result.returncode in (0, 3, 4), 'Cannot determine service state')
        return result.returncode == 0

    def control(self, action, role):
        self.call(['systemctl', action, self.service(role)])

    def port(self, name):
        return self.native['BYTEDEPTH_NATIVE_' + name.upper() + '_PORT']

    def http(self, method, path, payload=None, missing=False):
        url = f'http://127.0.0.1:{self.port("meili")}{path}'
        headers = {'Authorization': 'Bearer ' + self.env['BYTEDEPTH_TEST_MEILI_API_KEY']}
        data = None if payload is None else json.dumps(payload).encode()
        if data is not None:
            headers['Content-Type'] = 'application/json'
        try:
            with urlopen(Request(url, data=data, headers=headers, method=method), timeout=15) as response:
                content = response.read()
                return json.loads(content) if content else None
        except HTTPError as error:
            if error.code == 404 and missing:
                return None
            raise ToolError(f'Meili {method} request failed with HTTP {error.code}') from None

    def wait(self, role, url, public=False):
        for _ in range(30):
            try:
                if self.active(role):
                    self.call(['curl', '--fail', '--silent', '--show-error', '--connect-timeout', '3', '--max-time', '10', *(['--resolve', 'staging-bytedepth.bytedepth.cn:443:127.0.0.1'] if public else []), url + '/version'])
                    return
            except ToolError:
                pass
            time.sleep(1)
        raise ToolError(f'Service did not become healthy: {role}')


@contextlib.contextmanager
def lock(path, held=False):
    if held:
        yield
        return
    path = Path(path)
    require(path.parent.is_dir() and not path.parent.is_symlink(), 'Lock parent must already exist')
    descriptor = os.open(path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, 'a') as stream:
        require(stat.S_ISREG(os.fstat(stream.fileno()).st_mode), 'Lock must be a regular file')
        fcntl.flock(stream, fcntl.LOCK_EX)
        yield


def facts(io):
    ffmpeg = next((path for path in BROWSERS.glob('*/ffmpeg-linux') if path.is_file() and os.access(path, os.X_OK)), None)
    require(CHROMIUM.is_file() and os.access(CHROMIUM, os.X_OK) and ffmpeg, 'Shared Chromium/Playwright ffmpeg is unavailable')
    return {'package_lock_sha256': digest(io.root / 'package-lock.json'), 'maven_inputs_sha256': maven_inputs(io.root), 'chromium_version': io.call([str(CHROMIUM), '--version']).strip(), 'playwright_ffmpeg_version': io.call([str(ffmpeg), '-version']).splitlines()[0]}


class Timing:
    def __init__(self, file, identity):
        self.file, self.identity = Path(file), identity
        directory(self.file.parent)
        write(self.file, f'identity={identity}\n')

    def record(self, phase, result, start, finish):
        require(re.fullmatch(r'[a-z0-9_]+', phase) and result in ('passed', 'failed') and type(start) is int and type(finish) is int and 0 <= start <= finish, 'Invalid timing phase/result/interval')
        require(self.file.read_text().splitlines()[0] == f'identity={self.identity}', 'Timing identity changed')
        line = f'phase={phase} result={result} started_at_epoch_ms={start} finished_at_epoch_ms={finish} duration_ms={finish - start}\n'
        write(self.file, self.file.read_text() + line)
        print(f'TIMING phase={phase} result={result} duration_ms={finish - start}')

    @contextlib.contextmanager
    def phase(self, name):
        started = time.time_ns() // 1000000
        try:
            yield
        except BaseException:
            self.record(name, 'failed', started, max(started, time.time_ns() // 1000000))
            raise
        else:
            self.record(name, 'passed', started, max(started, time.time_ns() // 1000000))


def ensure(io, manifest, refresh=False):
    before = {'package_lock_sha256': digest(io.root / 'package-lock.json'), 'maven_inputs_sha256': maven_inputs(io.root)}
    try:
        expected = facts(io)
    except ToolError:
        expected = None
    if not refresh and expected and MAVEN.is_dir() and Path(manifest).is_file() and values(manifest) == expected:
        return
    identity = io.env.get('BYTEDEPTH_TEST_CANDIDATE_SHA') or (io.root / '.release-platform-source-commit').read_text().strip()
    require(SHA.fullmatch(identity), 'Runtime preparation requires full source identity')
    timing = Timing(Path(manifest).parent / 'timing' / identity, identity)
    common = [str(io.root / 'mvnw'), '-s', '.mvn/settings.xml', f'-Dmaven.repo.local={MAVEN}', '-Dsort.skip=true']
    probe = ['-Pstaging-integration', 'verify', '-Dtest=staging_bootstrap_dependency_probe', '-Dit.test=staging_bootstrap_dependency_probe', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dfailsafe.failIfNoSpecifiedTests=false', '-DskipTests=false']
    commands = [['clean', 'install', '-DskipTests'], ['dependency:go-offline', '-DincludePlugins=true', '-DincludePluginDependencies=true', '-DskipTests'], ['dependency:get', '-Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.2.5', '-Dtransitive=true'], probe, ['-o', *probe], ['verify', '-DskipTests'], ['-Pstaging-integration', 'verify', '-DskipTests'], ['-o', '-Pstaging-integration', 'verify', '-DskipTests']]
    with timing.phase('bootstrap_total'):
        directory(MAVEN, 0o755)
        with timing.phase('maven_runtime_prepare'), lock('/opt/shared-maven/repository.lock'):
            for command in commands:
                io.stream(common + command)
        with timing.phase('node_runtime_prepare'):
            io.stream(['npm', 'ci', '--ignore-scripts', '--no-audit', '--no-fund'])
        with timing.phase('playwright_runtime_prepare'):
            io.stream(['npm', 'exec', '--no', '--', 'playwright', 'install', 'ffmpeg'])
        after = facts(io)
        require(all(after[key] == value for key, value in before.items()), 'Runtime dependency inputs changed during preparation')
        directory(Path(manifest).parent)
        write(manifest, ''.join(f'{key}={value}\n' for key, value in after.items()))
