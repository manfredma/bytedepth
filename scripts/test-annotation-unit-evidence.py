#!/usr/bin/env python3
"""Exercise the evidence verifier with independent report fixtures."""
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest

CHECKER = Path(__file__).with_name('verify-annotation-unit-evidence.py').resolve()
MODULES = ['annotation.js', 'annotation-document.js', 'annotation-display.js',
           'annotation-render-lifecycle.js', 'post-mermaid.js']


class EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.started = time.time_ns() - 1_000_000_000
        self.cases = {'schemaVersion': 1, 'cases': {
            f'U{i:02}': [{'runner': 'vitest', 'suite': 'src/unit.test.js', 'name': 'suite behavior'},
                        {'runner': 'surefire', 'suite': 'example.UnitTest', 'name': 'behavior'}]
            for i in range(1, 47)}}
        self.vitest = {'success': True, 'startTime': self.started // 1_000_000 + 1,
                       'numTotalTests': 1, 'numPassedTests': 1, 'numFailedTests': 0,
                       'numPendingTests': 0, 'numTodoTests': 0,
                       'testResults': [{'name': '/repo/src/unit.test.js', 'assertionResults': [
                           {'fullName': 'suite behavior', 'status': 'passed'}]}]}
        self.coverage = {f'/repo/bytedepth-start/src/main/resources/static/js/{name}': {
            's': {'0': 1}, 'b': {'0': [1, 1]}, 'f': {'0': 1},
            'statementMap': {'0': {'start': {'line': 1}, 'end': {'line': 1}}},
            'branchMap': {'0': {}}, 'fnMap': {'0': {}}} for name in MODULES}
        self.xml = '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="example.UnitTest" name="behavior"/></testsuite>'

    def invoke(self, *, stale=False, wrong_run=False, corrupt=False):
        reports = self.root / 'module/target/surefire-reports'
        reports.mkdir(parents=True, exist_ok=True)
        paths = [self.root/'vitest.json', self.root/'coverage.json', reports/'TEST-example.UnitTest.xml']
        paths[0].write_text(json.dumps(self.vitest))
        paths[1].write_text(json.dumps(self.coverage))
        paths[2].write_text(self.xml)
        if stale:
            os.utime(paths[2], ns=(self.started-1, self.started-1))
        context = {'runId': 'run-current', 'startedAtNs': self.started}
        (self.root/'context.json').write_text(json.dumps(context))
        (self.root/'cases.json').write_text(json.dumps(self.cases))
        seal = {'runId': 'wrong' if wrong_run else 'run-current', 'reports': {
            str(p.resolve()): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}}
        (self.root/'seal.json').write_text(json.dumps(seal))
        if corrupt:
            paths[0].write_text(paths[0].read_text()+' ')
        return subprocess.run(['python3', str(CHECKER), '--vitest', str(paths[0]),
            '--surefire-root', str(self.root), '--coverage', str(paths[1]),
            '--cases', str(self.root/'cases.json'), '--run-context', str(self.root/'context.json'),
            '--seal', str(self.root/'seal.json')], capture_output=True, text=True)

    def test_complete_reports_pass(self):
        result = self.invoke()
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)

    def test_missing_u15_rejected(self):
        del self.cases['cases']['U15']
        self.assertNotEqual(self.invoke().returncode, 0)

    def test_zero_tests_rejected(self):
        self.vitest.update(numTotalTests=0, numPassedTests=0, testResults=[])
        self.assertNotEqual(self.invoke().returncode, 0)

    def test_each_required_case_must_execute(self):
        self.cases['cases']['U15'][0]['name']='not executed'
        self.assertNotEqual(self.invoke().returncode, 0)

    def test_skipped_or_failed_reports_rejected(self):
        original=copy.deepcopy(self.vitest)
        for status in ['pending','skipped','todo','failed']:
            with self.subTest(status=status):
                self.vitest=copy.deepcopy(original)
                self.vitest['testResults'][0]['assertionResults'][0]['status']=status
                self.assertNotEqual(self.invoke().returncode, 0)
        self.vitest=original
        for tag in ['skipped','failure','error']:
            with self.subTest(tag=tag):
                self.xml=f'<testsuite tests="1"><testcase classname="example.UnitTest" name="behavior"><{tag}/></testcase></testsuite>'
                self.assertNotEqual(self.invoke().returncode, 0)

    def test_missing_or_empty_or_uncovered_product_module_rejected(self):
        original=copy.deepcopy(self.coverage)
        for module in MODULES:
            for mutation in ['missing','empty','uncovered','function']:
                with self.subTest(module=module,mutation=mutation):
                    self.coverage=copy.deepcopy(original)
                    key=next(k for k in self.coverage if k.endswith('/'+module))
                    if mutation=='missing': del self.coverage[key]
                    elif mutation=='empty': self.coverage[key]['s']={}
                    elif mutation=='uncovered': self.coverage[key]['b']['0'][0]=0
                    else: self.coverage[key]['f']['0']=0
                    self.assertNotEqual(self.invoke().returncode, 0)

    def test_stale_mismatched_or_changed_reports_rejected(self):
        self.assertNotEqual(self.invoke(stale=True).returncode,0)
        self.assertNotEqual(self.invoke(wrong_run=True).returncode,0)
        self.assertNotEqual(self.invoke(corrupt=True).returncode,0)


if __name__=='__main__':
    unittest.main()
