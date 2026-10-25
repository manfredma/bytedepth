"""共用 staging 事务：核验、隔离测试、恢复并生成证明。"""
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import secrets
import shutil
import tempfile
import time
from urllib.parse import urlsplit

from ..process import ToolError, digest
from . import resources, runtime
from .runtime import PUBLIC, require

SLOT_ENV = Path('/run/bytedepth/staging-native-e2e.env')
RESOLVE = 'staging-bytedepth.bytedepth.cn:443:127.0.0.1'


def new_run():
    return datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S_') + secrets.token_hex(4)


def verify_hearth(io):
    base = ['curl', '--silent', '--show-error', '--connect-timeout', '3', '--max-time', '10', '--output', '/dev/null', '--write-out', '%{http_code}\n%{redirect_url}']
    local = ['--resolve', RESOLVE, '--header', 'X-Bytedepth-Traffic: verification']
    require(io.call(base + local + [PUBLIC + '/login']).strip() == '302\n' + PUBLIC + '/oauth2/authorization/hearth', 'Deployed login must start Hearth SSO')
    authorization = runtime.hearth_authorization(io.call(base + local + [PUBLIC + '/oauth2/authorization/hearth']).strip())
    status, separator, location = io.call(base + [authorization]).strip().partition('\n')
    url = urlsplit(location)
    require(separator and status == '302' and url.scheme == 'https' and url.netloc == 'staging-hearth.bytedepth.cn' and url.path == '/login', 'Hearth rejected staging client or authorization request')
    print('Deployed Hearth entry and registered client passed (authorization request only).')


def restore(io):
    failures = []
    # 即使清理阶段已启动应用，也必须恢复边缘路由。
    for action in [lambda: io.control('start', 'app'), lambda: io.wait('app', 'http://127.0.0.1:' + io.port('app')), lambda: io.control('start', 'edge'), lambda: io.wait('edge', 'http://127.0.0.1:' + io.port('edge'))]:
        try:
            action()
        except Exception as error:
            failures.append(error)
    require(not failures, 'Staging app or edge restoration failed')


def load_profile(io, file, phase):
    runtime.private(file)
    data = runtime.values(file)
    require(all(re.fullmatch(r'[A-Z][A-Z0-9_]*', key) for key in data) and data.get('SPRING_PROFILES_ACTIVE') == 'staging-' + phase, 'Invalid generated test profile')
    io.env.update(data)


def diagnostics(io, since):
    # 只读且有界的诊断；诊断失败不得改变原始结果。
    print('E2E_SEARCH_DIAGNOSTIC begin')
    for args in [
        ['timeout', '8', 'systemctl', 'show', io.service('meilisearch'), '--property=ActiveState,Result,NRestarts,ExecMainStatus,MemoryCurrent,MemoryPeak,MemoryMax,CPUUsageNSec'],
        ['timeout', '8', 'journalctl', '--unit=' + io.service('meilisearch'), '--since=' + since, '--no-pager', '--output=short-iso', '--lines=80'],
    ]:
        try:
            io.stream(args)
        except Exception:
            print('E2E_SEARCH_DIAGNOSTIC: diagnostic command unavailable')
    for metric in ['/proc/pressure/memory', '/proc/pressure/io', '/sys/fs/cgroup/system.slice/bytedepth-staging-native-meilisearch.service/memory.events']:
        path = Path(metric)
        try:
            if path.is_file():
                print(f'E2E_SEARCH_DIAGNOSTIC {path.name}: {path.read_text()[:4096]}')
        except OSError:
            print(f'E2E_SEARCH_DIAGNOSTIC metric unavailable: {path.name}')
    try:
        started = time.monotonic()
        io.http('GET', '/health')
        print(f'E2E_SEARCH_DIAGNOSTIC health=passed milliseconds={int((time.monotonic() - started) * 1000)}')
        response = io.http('GET', '/tasks?indexUids=' + io.env['BYTEDEPTH_STAGING_E2E_SEARCH_INDEX'] + '&limit=5')
        fields = ('uid', 'indexUid', 'status', 'type', 'enqueuedAt', 'startedAt', 'finishedAt', 'duration')
        def metadata(task):
            return {**{key: task.get(key) for key in fields}, 'error': {key: (task.get('error') or {}).get(key) for key in ('code', 'type')}}
        print('E2E_SEARCH_DIAGNOSTIC tasks: ' + json.dumps([metadata(task) for task in response.get('results', [])]))
        journal = io.call(['timeout', '8', 'journalctl', '--unit=' + io.service('test-slot'), '--since=' + since, '--no-pager', '--output=cat', '--lines=2000'])
        for task in sorted(set(re.findall(r'127\.0\.0\.1:[0-9]+/tasks/([0-9]+)', journal)))[:5]:
            print('E2E_SEARCH_DIAGNOSTIC task: ' + json.dumps(metadata(io.http('GET', '/tasks/' + task))))
    except Exception:
        print('E2E_SEARCH_DIAGNOSTIC: Meili metadata unavailable')
    print('E2E_SEARCH_DIAGNOSTIC end')


def e2e(io, data, work, state):
    load_profile(io, data['e2e_env'], 'e2e')
    jar = Path(io.env.get('RELEASE_PLATFORM_TEST_JAR', str(io.root.parent / 'app.jar')))
    require(jar.is_file() and not jar.is_symlink(), 'Deployed test-slot JAR is unavailable')
    runtime.directory(SLOT_ENV.parent, 0o755)
    content = Path(data['e2e_env']).read_text() + f'BYTEDEPTH_TEST_SLOT_JAR={jar}\nBYTEDEPTH_TEST_SLOT_SHA256={digest(jar)}\n'
    runtime.write(SLOT_ENV, content)
    state['since'] = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')
    state['slot_attempted'] = True
    io.control('start', 'test-slot')
    io.wait('test-slot', PUBLIC, public=True)
    posts = io.call(['curl', '--fail', '--silent', '--show-error', '--connect-timeout', '3', '--max-time', '10', '--resolve', RESOLVE, PUBLIC + '/posts'])
    slug = re.search(r'href="/posts/([a-z0-9-]+)"', posts)
    require(slug, 'No usable public article slug for E2E')
    io.env.update(E2E_BASE_URL=PUBLIC, E2E_LOCAL_HOST_RESOLVER_RULE='MAP staging-bytedepth.bytedepth.cn 127.0.0.1', E2E_POST_SLUG=slug[1], E2E_ADMIN_USERNAME=io.env['BYTEDEPTH_STAGING_E2E_USERNAME'], E2E_ADMIN_PASSWORD=io.env['BYTEDEPTH_STAGING_E2E_PASSWORD'], PLAYWRIGHT_JSON_OUTPUT_FILE=str(work / 'playwright-results.json'), PLAYWRIGHT_CHROMIUM_EXECUTABLE=str(runtime.CHROMIUM))
    try:
        io.stream(['npm', 'run', 'test:e2e', '--', '--reporter=list,json'], log=work / 'playwright.log')
    except Exception:
        diagnostics(io, state['since'])
        raise
    return runtime.test_count('e2e', work / 'playwright-results.json')


def cleanup(io, file, state, work):
    failures = []
    if state['slot_attempted']:
        try:
            if io.active('test-slot'):
                io.control('stop', 'test-slot')
            require(not io.active('test-slot'), 'Test slot remained active')
        except Exception as error:
            failures.append(error)
    if state['since']:
        try:
            io.stream(['journalctl', '--unit=' + io.service('test-slot'), '--since=' + state['since'], '--no-pager', '--output=short-iso'], log=work / 'test-slot-journal.log')
        except Exception as error:
            failures.append(error)
    if file.is_file():
        try:
            resources.teardown(io, file)
        except Exception as error:
            failures.append(error)
    if state['app_stop_attempted']:
        try:
            restore(io)
        except Exception as error:
            failures.append(error)
    if state['slot_attempted']:
        try:
            SLOT_ENV.unlink(missing_ok=True)
        except Exception as error:
            failures.append(error)
    require(not failures, 'Test cleanup, journal policy or staging restoration failed; no evidence written')


def execute(io, sha, phase, state_dir, evidence_dir, runtime_manifest):
    require(phase in ('integration', 'e2e'), 'Unknown staging phase')
    evidence_dir = runtime.directory(evidence_dir)
    evidence = evidence_dir / ('staging-' + phase)
    evidence.unlink(missing_ok=True)
    runtime.candidate(io.root, sha, io.env)
    if phase == 'e2e':
        require(io.env.get('BYTEDEPTH_STAGING_E2E_USERNAME') and io.env.get('BYTEDEPTH_STAGING_E2E_PASSWORD'), 'Explicit E2E account required')
        verify_hearth(io)
    runtime.ensure(io, runtime_manifest)
    require(shutil.disk_usage(io.root).free >= 2 * 1024**3, 'Staging workspace requires at least 2 GiB free')
    resources.inputs(io)
    if phase == 'e2e':
        io.stream(['npm', 'ci', '--ignore-scripts', '--no-audit', '--no-fund'])
    state_dir = runtime.directory(state_dir)
    slots = runtime.directory(io.env.get('RELEASE_PLATFORM_TEST_STATE_DIR', '/var/lib/bytedepth-staging/test-slots'))
    run = new_run()
    file = slots / run / 'manifest'
    io.env.update(BYTEDEPTH_TEST_STATE_DIR=str(slots), BYTEDEPTH_TEST_CANDIDATE_SHA=sha, BYTEDEPTH_TEST_SLOT_LOCK_HELD='1', BYTEDEPTH_DEPLOY_MODE='staging')
    state = {'app_stop_attempted': False, 'slot_attempted': False, 'since': ''}
    with tempfile.TemporaryDirectory(prefix='.staging-' + phase + '.', dir=state_dir) as temporary:
        work = Path(temporary)
        try:
            state['app_stop_attempted'] = True
            io.control('stop', 'app')
            require(not io.active('app'), 'Staging app remained active; resources not provisioned')
            data = resources.provision(io, run, file)
            manifest_sha = digest(file)
            io.env['BYTEDEPTH_TEST_MANIFEST'] = str(file)
            if phase == 'integration':
                load_profile(io, data['it_env'], 'it')
                for report in io.root.glob('**/target/failsafe-reports/failsafe-summary.xml'):
                    report.unlink()
                io.stream([str(io.root / 'mvnw'), '-o', '-Pstaging-integration', 'verify', f'-Dmaven.repo.local={runtime.MAVEN}'], log=work / 'maven.log')
                count = runtime.test_count('integration', io.root)
            else:
                count = e2e(io, data, work, state)
        finally:
            cleanup(io, file, state, work)
    if phase == 'e2e':
        verify_hearth(io)
    runtime.candidate(io.root, sha, io.env)
    # 只有清理成功且身份不可变的候选才能生成回执。
    fields = {'commit': sha, 'command': f'run-staging-{phase}-tests', 'timestamp': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'), 'result': 'passed', 'runtime_mode': 'host-native', 'run_id': run, 'test_resource_manifest_sha': manifest_sha, 'cleanup': 'result=passed', 'artifact_digest': io.env['ARTIFACT_DIGEST'], 'attempt_nonce': io.env['RELEASE_PLATFORM_TASK_NONCE'], 'tests_total': str(count)}
    runtime.write(evidence, ''.join(f'{key}={value}\n' for key, value in fields.items()))
