#!/usr/bin/env python3
"""Kill known annotation safety regressions using the formal unit suites."""
import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
JS = 'bytedepth-start/src/main/resources/static/js/'
APP = 'bytedepth-app/src/main/java/manfred/bytedepth/app/annotation/'
TEST_JS = 'bytedepth-start/src/test/js/'


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


def run():
    self_test()
    java_home = subprocess.check_output(['bash', '-c',
        'source "$1/scripts/lib/java-25.sh"; resolve_java_25', '--', str(ROOT)], text=True).strip()
    env = dict(os.environ, JAVA_HOME=java_home)
    with tempfile.TemporaryDirectory(prefix='bytedepth-annotation-mutations-') as temp:
        sandbox = Path(temp)/'repo'
        # Copy only reviewed source inputs, never ignored credentials or runtime artifacts.
        names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
                                        cwd=ROOT).decode().split('\0')
        for name in set(names) - {''}:
            source = ROOT/name
            if source.is_file():
                destination = sandbox/name
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)
        def execute(command, log):
            with log.open('w') as output:
                result = subprocess.run(command, cwd=sandbox, env=env, stdout=output, stderr=subprocess.STDOUT)
            if 'WARNING' in log.read_text():
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


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        run()
