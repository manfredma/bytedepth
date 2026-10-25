#!/usr/bin/env python3
"""校验实际单测执行与覆盖率，并绑定当前质量运行。"""
from dataclasses import dataclass
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
import time
import uuid
import xml.etree.ElementTree as ET

from . import ROOT
from .process import ToolError, atomic_write, digest, warning_lines

MODULES = ('annotation.js', 'annotation-document.js', 'annotation-display.js',
           'annotation-render-lifecycle.js', 'post-mermaid.js', 'sw.js')
CASE_IDS = {f'U{i:02}' for i in range(1, 47)}
JS = 'bytedepth-start/src/main/resources/static/js/'
APP = 'bytedepth-app/src/main/java/manfred/bytedepth/app/annotation/'
TEST_JS = 'bytedepth-start/src/test/js/'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load(path):
    return json.loads(path.read_text())


def report_paths(args):
    xmls = sorted(args.surefire_root.glob('*/target/surefire-reports/TEST-*.xml'))
    require(xmls, 'No Surefire reports executed')
    return [args.vitest.resolve(), args.coverage.resolve(), *(p.resolve() for p in xmls)]


def verify_fresh(paths, context):
    require(isinstance(context.get('runId'), str) and context['runId'] and type(context.get('startedAtNs')) is int and context['startedAtNs'] > 0, 'Invalid run context')
    for path in paths:
        require(path.stat().st_mtime_ns >= context['startedAtNs'], f'Stale report: {path}')


def verify(args):
    context = load(args.run_context)
    paths = report_paths(args)
    verify_fresh(paths, context)
    expected_seal = {'runId': context['runId'], 'reports': {str(p): digest(p) for p in paths}}
    if not args.record_seal:
        seal = load(args.seal)
        require(seal.get('runId') == context['runId'], 'Report runId mismatch')
        require(seal.get('reports') == expected_seal['reports'], 'Report seal changed or incomplete')

    manifest = load(args.cases)
    require(manifest.get('schemaVersion') == 1 and set(manifest.get('cases', {})) == CASE_IDS,
            'Expected exactly U01-U46 (including U15) in unit manifest')
    vitest = load(args.vitest)
    for key in ('numTotalTests', 'numPassedTests', 'numFailedTests', 'numPendingTests', 'numTodoTests', 'startTime'):
        require(type(vitest.get(key)) is int and 0 <= vitest[key] <= 2**53 - 1, f'Invalid Vitest counter: {key}')
    require(vitest.get('success') is True and vitest.get('numTotalTests', 0) > 0, 'Vitest failed or zero tests')
    require(vitest.get('startTime', 0) * 1_000_000 >= context['startedAtNs'] - 1_000_000,
            'Stale Vitest startTime')
    require(all(vitest.get(k, 0) == 0 for k in ['numFailedTests', 'numPendingTests', 'numTodoTests']),
            'Vitest has failures, skip or todo')
    executed = set()
    total = 0
    for suite in vitest['testResults']:
        for case in suite['assertionResults']:
            require(case['status'] == 'passed', f'Vitest case did not pass: {case["fullName"]}')
            identity = ('vitest', Path(suite['name']).as_posix(), case['fullName'])
            require(identity not in executed, 'Duplicate Vitest execution identity')
            executed.add(identity)
            total += 1
    require(total == vitest['numTotalTests'] == vitest['numPassedTests'], 'Vitest execution count mismatch')
    java_total = 0
    for path in paths[2:]:
        suite = ET.parse(path).getroot()
        cases = list(suite.iter('testcase'))
        require(int(suite.get('tests', -1)) == len(cases), f'Surefire execution count mismatch: {path}')
        require(all(int(suite.get(k, 0)) == 0 for k in ['failures', 'errors', 'skipped']),
                f'Surefire failures or skip: {path}')
        for case in cases:
            require(not any(case.find(k) is not None for k in ['failure', 'error', 'skipped']),
                    f'Surefire case did not pass: {case.get("name")}')
            identity = ('surefire', case.attrib['classname'], case.attrib['name'])
            require(identity not in executed, 'Duplicate Surefire execution identity')
            executed.add(identity)
            java_total += 1
    require(java_total > 0, 'Zero Java unit tests')
    for case_id, references in manifest['cases'].items():
        require(isinstance(references, list) and references, f'{case_id}: no tests mapped')
        for ref in references:
            runner, suite, name = ref['runner'], ref['suite'], ref['name']
            require(runner in ('vitest', 'surefire'), f'{case_id}: unknown runner')
            found = any(r == runner and n == name and (s == suite if runner == 'surefire'
                        else s.endswith('/'+suite)) for r, s, n in executed)
            require(found, f'{case_id}: case not executed and passed: {suite} :: {name}')

    coverage = load(args.coverage)
    for module in MODULES:
        suffix = '/bytedepth-start/src/main/resources/static/'+('' if module == 'sw.js' else 'js/')+module
        matches = [value for path, value in coverage.items() if path.replace('\\', '/').endswith(suffix)]
        require(len(matches) == 1, f'Missing or duplicate product coverage: {module}')
        record = matches[0]
        for counts, mapping in [('s', 'statementMap'), ('b', 'branchMap'), ('f', 'fnMap')]:
            require(record.get(counts) and set(record[counts]) == set(record.get(mapping, {})),
                    f'Empty or inconsistent {counts} coverage: {module}')
            values = record[counts].values()
            if counts == 'b':
                require(all(isinstance(value, list) and value for value in values), f'Empty branch: {module}')
                values = [n for value in record[counts].values() for n in value]
            require(all(type(n) is int and 0 < n <= 2**53 - 1 for n in values),
                    f'Uncovered {counts}: {module}')
    if args.record_seal:
        require(expected_seal['reports'] == {str(p): digest(p) for p in paths}, 'Reports changed during verification')
        atomic_write(args.seal, json.dumps(expected_seal, indent=2) + '\n')
    print(f'Annotation unit evidence PASS: U01-U46, {total} JS / {java_total} Java cases, '
          f'{len(MODULES)} product modules fully covered, runId={context["runId"]}')



def begin(path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    atomic_write(path, json.dumps({'runId': str(uuid.uuid4()), 'startedAtNs': time.time_ns()}) + '\n')


@dataclass(frozen=True)
class Mutation:
    name: str
    path: str
    before: str
    after: str
    suite: str
    test: str
    java: bool = False


MUTATIONS = [
    Mutation('remove-quote-check', JS+'annotation-document.js',
        'codepoints(model.text).slice(start, end).join("") !== view.anchor.exact || !view.anchor.exact.trim()',
        'false', TEST_JS+'annotation-document.test.js', 'U09_U12_invalidRangesModelsAndUnknownRulesFailClosed'),
    Mutation('raw-page-text', JS+'annotation-document.js',
        'let { start, end } = bounds(spans);\n        const text = codepoints(model.text);',
        'let { start, end } = bounds(spans);\n        const text = codepoints(root.textContent);',
        TEST_JS+'annotation-document.test.js', 'U13_U14_copyLabelsLanguageAndLineNumbersCannotShiftCodeCoordinates'),
    Mutation('first-duplicate', APP+'AnnotationResolver.java',
        'if (oldByKey.get(block.key()).size() != 1 || candidates.size() != 1)\n'
        '                return failed(id, targetRevision, PlacementReason.DUPLICATE_TARGET);',
        '// UNSAFE MUTANT: take first duplicate', 'manfred.bytedepth.app.annotation.AnnotationResolverTest',
        'U07_oldOffsetsAndFirstCandidateCannotResolveEquivalentDuplicates', True),
    Mutation('delete-unresolved', APP+'ListAnnotationsQryExe.java',
        'placements.save(resolved);',
        'if (resolved.status() != manfred.bytedepth.domain.annotation.PlacementStatus.ANCHORED) '
        'annotationRepository.delete(annotation.id());\n                    placements.save(resolved);',
        'manfred.bytedepth.app.annotation.AnnotationCommandProtocolTest', 'U04_U11_resolveDeletedTextKeepsCommentAndOriginalAnchor', True),
    Mutation('ignore-revision', APP+'CreateAnnotationCmdExe.java',
        'if (anchor.sourceRevision() != post.getContentVersion())\n'
        '                throw new AnnotationWriteConflictException(AnnotationWriteConflictException.Kind.STALE_REVISION);',
        '// UNSAFE MUTANT: accept stale page', 'manfred.bytedepth.app.annotation.AnnotationCommandProtocolTest',
        'U33_U36_lockedLatestVersionRejectsStaleAndWritesNothing', True),
    Mutation('accept-old-generation', JS+'annotation.js',
        '.then((saved) => {\n        if (!active || !content.isConnected) {\n          return;\n        }\n        storeAnnotation(saved);\n        renderMarks();\n        if (session === composerSession) {',
        '.then((saved) => {\n        storeAnnotation(saved);\n        renderMarks();\n        if (session === composerSession) {', TEST_JS+'annotation.test.js',
        'U19_U30_oldcommentSuccessCannotChangeReinitializedUi'),
    Mutation('cache-dynamic-annotation-responses', 'bytedepth-start/src/main/resources/static/sw.js',
        'if (!fingerprintedStatic && !PRECACHE_URLS.includes(url.pathname)) return;',
        '// UNSAFE MUTANT: cache every dynamic GET as a static asset',
        TEST_JS+'service-worker.test.js', 'U11_U38_annotationResponsesAlwaysReachCurrentRevisionAndIdentity'),
    Mutation('accept-reserved-numeric-slug',
        'bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/Post.java',
        'return SlugUtils.isValid(slug) && !slug.matches("\\\\d+");',
        'return SlugUtils.isValid(slug);',
        'manfred.bytedepth.domain.post.PostTest',
        'numericSlugCannotCreateAnArticleThatCollidesWithTheReservedIdRoute', True),
]


def patch(source, mutation):
    if source.count(mutation.before) != 1:
        raise ValueError(f'{mutation.name}: expected exactly one mutation site')
    return source.replace(mutation.before, mutation.after, 1)


def assertion_failure(report, mutation):
    if mutation.java:
        root = ET.parse(report).getroot()
        cases = [c for c in root.iter('testcase') if c.get('name') == mutation.test]
        if len(cases) != 1:
            return False
        failure = cases[0].find('failure')
        return failure is not None and any(token in failure.get('type', '') for token in
            ['AssertionFailedError', 'AssertionError', 'NeverWantedButInvoked', 'TooManyActualInvocations'])
    data = json.loads(report.read_text())
    cases = [case for suite in data['testResults'] for case in suite['assertionResults']
             if case.get('title') == mutation.test or case.get('fullName', '').endswith(' '+mutation.test)]
    return len(cases) == 1 and cases[0]['status'] == 'failed' and any(
        'AssertionError' in message for message in cases[0].get('failureMessages', []))


def passed_case(report, mutation):
    if mutation.java:
        cases = [case for case in ET.parse(report).getroot().iter('testcase')
                 if case.get('name') == mutation.test]
        return len(cases) == 1 and all(cases[0].find(tag) is None
                                      for tag in ['failure', 'error', 'skipped'])
    data = json.loads(report.read_text())
    cases = [case for suite in data['testResults'] for case in suite['assertionResults']
             if case.get('title') == mutation.test or case.get('fullName', '').endswith(' '+mutation.test)]
    return len(cases) == 1 and cases[0]['status'] == 'passed'


def self_test():
    with tempfile.TemporaryDirectory(prefix='bytedepth-mutation-check-') as temp:
        report = Path(temp)/'report.json'
        mutation = MUTATIONS[0]
        patch(mutation.before, mutation)
        try:
            patch('mutation site disappeared', mutation)
        except ValueError:
            pass
        else:
            raise AssertionError('Missing mutation site accepted')
        for status, message, expected in [('passed', '', False), ('failed', 'SyntaxError: bad', False),
                                           ('failed', 'TypeError: bad', False), ('failed', 'AssertionError: quote', True)]:
            report.write_text(json.dumps({'testResults': [{'assertionResults': [
                {'title': mutation.test, 'status': status, 'failureMessages': [message]}]}]}))
            assert assertion_failure(report, mutation) is expected
            assert passed_case(report, mutation) is (status == 'passed')
    print('Mutation checker self-test PASS (missing site, survivor and syntax/runtime errors rejected)', flush=True)


def mutations(root=ROOT):
    self_test()
    from .quality import java_home
    resolved_java = java_home()
    env = dict(os.environ, JAVA_HOME=resolved_java)
    with tempfile.TemporaryDirectory(prefix='bytedepth-annotation-mutations-') as temp:
        sandbox = Path(temp)/'repo'
        # 仅复制已审阅的源码输入，不复制被忽略的凭据或运行产物。
        names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
                                        cwd=root).decode().split('\0')
        for name in set(names) - {''}:
            source = root/name
            if source.is_file():
                destination = sandbox/name
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)
        def execute(command, log):
            with log.open('w') as output:
                result = subprocess.run(command, cwd=sandbox, env=env, stdout=output, stderr=subprocess.STDOUT)
            if warning_lines(log.read_text()):
                raise RuntimeError(f'Warning during mutation verification: {log.read_text()[-4000:]}')
            return result.returncode
        log = Path(temp)/'install.log'
        if execute(['npm', 'ci', '--ignore-scripts', '--no-audit', '--no-fund'], log):
            raise RuntimeError(log.read_text()[-4000:])
        for mutation in MUTATIONS:
            source_path = sandbox/mutation.path
            original = source_path.read_text()
            if mutation.java:
                module = mutation.path.split('/')[0]
                command = ['./mvnw', '-pl', module, '-am', 'test',
                    f'-Dtest={mutation.suite}#{mutation.test}', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dsort.skip=true']
                report = sandbox/f'{module}/target/surefire-reports/TEST-{mutation.suite}.xml'
            else:
                command = ['npx', 'vitest', 'run', mutation.suite, '-t', mutation.test,
                    '--reporter=json', '--outputFile=target/mutation-vitest.json']
                report = sandbox/'target/mutation-vitest.json'
            for phase in ['baseline', 'mutant', 'restored']:
                source_path.write_text(patch(original, mutation) if phase == 'mutant' else original)
                report.unlink(missing_ok=True)
                log = Path(temp)/f'{mutation.name}-{phase}.log'
                code = execute(command, log)
                if phase == 'mutant':
                    if code == 0 or not report.exists() or not assertion_failure(report, mutation):
                        raise RuntimeError(f'{mutation.name}: no behavioral assertion killed mutant (exit={code})\n{log.read_text()[-2000:]}\n{report.read_text()[-6000:] if report.exists() else "NO REPORT"}')
                elif code != 0 or not report.exists() or not passed_case(report, mutation):
                    raise RuntimeError(f'{mutation.name}: {phase} did not pass\n{log.read_text()[-4000:]}')
            print(f'KILLED {mutation.name}: baseline PASS -> assertion FAIL -> restored PASS', flush=True)
    print(f'Annotation mutations PASS: {len(MUTATIONS)}/{len(MUTATIONS)} killed by formal unit assertions', flush=True)

