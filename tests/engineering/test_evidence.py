#!/usr/bin/env python3
"""使用独立报告夹具验证证据校验器。"""
from contextlib import redirect_stdout
import copy
import io
import importlib
from types import SimpleNamespace
import xml.etree.ElementTree as ET
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest

CHECKER = None
MODULES = ['annotation.js', 'annotation-document.js', 'annotation-display.js',
           'annotation-render-lifecycle.js', 'post-mermaid.js', 'sw.js']


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
        self.coverage = {f'/repo/bytedepth-start/src/main/resources/static/{"" if name == "sw.js" else "js/"}{name}': {
            's': {'0': 1}, 'b': {'0': [1, 1]}, 'f': {'0': 1},
            'statementMap': {'0': {'start': {'line': 1}, 'end': {'line': 1}}},
            'branchMap': {'0': {}}, 'fnMap': {'0': {}}} for name in MODULES}
        self.xml = '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="example.UnitTest" name="behavior"/></testsuite>'

    def invoke(self, *, stale=False, wrong_run=False, corrupt=False, record_seal=False, existing_seal=None):
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
        if existing_seal is not None:
            (self.root/'seal.json').write_text(existing_seal)
        if corrupt:
            paths[0].write_text(paths[0].read_text()+' ')
        verify = importlib.import_module('tools.bytedepth.evidence').verify
        args = SimpleNamespace(vitest=paths[0], surefire_root=self.root, coverage=paths[1],
            cases=self.root/'cases.json', run_context=self.root/'context.json', seal=self.root/'seal.json', record_seal=record_seal)
        try:
            with redirect_stdout(io.StringIO()):
                verify(args)
            return 0
        except (ValueError, OSError, KeyError, TypeError, ET.ParseError):
            return 1


    def test_complete_reports_pass(self):
        result = self.invoke()
        self.assertEqual(result, 0)

    def test_missing_u15_rejected(self):
        del self.cases['cases']['U15']
        self.assertNotEqual(self.invoke(), 0)

    def test_zero_tests_rejected(self):
        self.vitest.update(numTotalTests=0, numPassedTests=0, testResults=[])
        self.assertNotEqual(self.invoke(), 0)

    def test_each_required_case_must_execute(self):
        self.cases['cases']['U15'][0]['name']='not executed'
        self.assertNotEqual(self.invoke(), 0)

    def test_skipped_or_failed_reports_rejected(self):
        original=copy.deepcopy(self.vitest)
        for status in ['pending','skipped','todo','failed']:
            with self.subTest(status=status):
                self.vitest=copy.deepcopy(original)
                self.vitest['testResults'][0]['assertionResults'][0]['status']=status
                self.assertNotEqual(self.invoke(), 0)
        self.vitest=original
        for tag in ['skipped','failure','error']:
            with self.subTest(tag=tag):
                self.xml=f'<testsuite tests="1"><testcase classname="example.UnitTest" name="behavior"><{tag}/></testcase></testsuite>'
                self.assertNotEqual(self.invoke(), 0)

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
                    self.assertNotEqual(self.invoke(), 0)

    def test_stale_mismatched_or_changed_reports_rejected(self):
        self.assertNotEqual(self.invoke(stale=True),0)
        self.assertNotEqual(self.invoke(wrong_run=True),0)
        self.assertNotEqual(self.invoke(corrupt=True),0)

    def test_rejected_reports_do_not_replace_previous_seal(self):
        previous = '{"runId":"previous-verified-run"}\n'
        self.vitest['success'] = False
        self.assertNotEqual(self.invoke(record_seal=True, existing_seal=previous), 0)
        self.assertEqual((self.root/'seal.json').read_text(), previous)

    def test_successful_reports_can_be_sealed(self):
        self.assertEqual(self.invoke(record_seal=True, existing_seal='{}'), 0)
        self.assertEqual(json.loads((self.root/'seal.json').read_text())['runId'], 'run-current')

    def test_mutation_syntax_error_and_survivor_are_rejected(self):
        from tools.bytedepth.evidence import self_test
        with redirect_stdout(io.StringIO()):
            self_test()

    def test_duplicate_execution_identities_never_replace_verified_seal(self):
        previous = '{"runId":"verified"}\n'
        self.vitest['testResults'][0]['assertionResults'] *= 2
        self.vitest.update(numTotalTests=2, numPassedTests=2)
        self.assertNotEqual(self.invoke(record_seal=True, existing_seal=previous), 0)
        self.assertEqual(previous, (self.root / 'seal.json').read_text())
        self.vitest['testResults'][0]['assertionResults'] = self.vitest['testResults'][0]['assertionResults'][:1]
        self.vitest.update(numTotalTests=1, numPassedTests=1)
        self.xml = '<testsuite tests="2"><testcase classname="example.UnitTest" name="behavior"/><testcase classname="example.UnitTest" name="behavior"/></testsuite>'
        self.assertNotEqual(self.invoke(record_seal=True, existing_seal=previous), 0)
        self.assertEqual(previous, (self.root / 'seal.json').read_text())

    def test_boolean_and_nonfinite_counters_never_replace_verified_seal(self):
        previous = '{"runId":"verified"}\n'
        original = copy.deepcopy(self.vitest)
        for key in ['numTotalTests', 'numPassedTests', 'numFailedTests', 'numPendingTests', 'numTodoTests', 'startTime']:
            self.vitest = copy.deepcopy(original)
            self.vitest[key] = bool(self.vitest[key])
            self.assertNotEqual(self.invoke(record_seal=True, existing_seal=previous), 0, key)
            self.assertEqual(previous, (self.root / 'seal.json').read_text())
        self.vitest = original
        for value in [True, float('inf'), float('nan'), 0.5, -1]:
            record = next(iter(self.coverage.values()))
            record['s']['0'] = value
            self.assertNotEqual(self.invoke(record_seal=True, existing_seal=previous), 0, repr(value))
            self.assertEqual(previous, (self.root / 'seal.json').read_text())


if __name__=='__main__':
    unittest.main()
