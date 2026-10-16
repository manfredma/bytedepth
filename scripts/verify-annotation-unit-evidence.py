#!/usr/bin/env python3
"""Validate actual unit executions and coverage, bound to one fresh quality run."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import time
import uuid
import xml.etree.ElementTree as ET

MODULES = ('annotation.js', 'annotation-document.js', 'annotation-display.js',
           'annotation-render-lifecycle.js', 'post-mermaid.js')
CASE_IDS = {f'U{i:02}' for i in range(1, 47)}


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
    require(context.get('runId') and isinstance(context.get('startedAtNs'), int), 'Invalid run context')
    for path in paths:
        require(path.stat().st_mtime_ns >= context['startedAtNs'], f'Stale report: {path}')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify(args):
    context = load(args.run_context)
    paths = report_paths(args)
    verify_fresh(paths, context)
    if args.record_seal:
        args.seal.write_text(json.dumps({'runId': context['runId'],
                                        'reports': {str(p): digest(p) for p in paths}}, indent=2)+'\n')
    seal = load(args.seal)
    require(seal.get('runId') == context['runId'], 'Report runId mismatch')
    require(seal.get('reports') == {str(p): digest(p) for p in paths}, 'Report seal changed or incomplete')

    manifest = load(args.cases)
    require(manifest.get('schemaVersion') == 1 and set(manifest.get('cases', {})) == CASE_IDS,
            'Expected exactly U01-U46 (including U15) in unit manifest')
    vitest = load(args.vitest)
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
            executed.add(('vitest', Path(suite['name']).as_posix(), case['fullName']))
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
            executed.add(('surefire', case.attrib['classname'], case.attrib['name']))
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
        suffix = '/bytedepth-start/src/main/resources/static/js/'+module
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
            require(all(isinstance(n, (int, float)) and n > 0 for n in values),
                    f'Uncovered {counts}: {module}')
    print(f'Annotation unit evidence PASS: U01-U46, {total} JS / {java_total} Java cases, '
          f'{len(MODULES)} product modules fully covered, runId={context["runId"]}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--begin', type=Path, help='Create a new quality-run context before any tests')
    for option in ['vitest', 'surefire-root', 'coverage', 'cases', 'run-context', 'seal']:
        parser.add_argument('--'+option, type=Path)
    parser.add_argument('--record-seal', action='store_true', help='Bind fresh reports to this run, then verify')
    args = parser.parse_args()
    if args.begin:
        args.begin.parent.mkdir(parents=True, exist_ok=True)
        args.begin.write_text(json.dumps({'runId': str(uuid.uuid4()), 'startedAtNs': time.time_ns()})+'\n')
        return
    require(all(getattr(args, key) for key in ['vitest', 'surefire_root', 'coverage', 'cases', 'run_context', 'seal']),
            'All report, manifest, context and seal arguments are required')
    verify(args)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, ET.ParseError) as error:
        print(f'Annotation unit evidence FAIL: {error}', file=sys.stderr)
        sys.exit(1)
