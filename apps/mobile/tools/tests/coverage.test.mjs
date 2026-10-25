import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { verifyMobileCoverage } from "../checks.mjs";

test("coverage rejects missing, empty and uncovered owned source", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "mobile-coverage-"));
  try {
    for (const directory of ["src", "plugins", "coverage"]) fs.mkdirSync(path.join(root, directory));
    for (const file of ["src/example.ts", "App.tsx"]) fs.writeFileSync(path.join(root, file), "export {};");
    const report = path.join(root, "coverage/coverage-final.json");
    const valid = Object.fromEntries(
      ["src/example.ts", "App.tsx"].map((file) => [
        path.join(root, file),
        { s: { 0: 1 }, f: { 0: 1 }, b: { 0: [1, 1] }, statementMap: { 0: {} }, fnMap: { 0: {} }, branchMap: { 0: {} } },
      ]),
    );
    fs.writeFileSync(report, JSON.stringify(valid));
    assert.doesNotThrow(() => verifyMobileCoverage(root));
    const branchless = structuredClone(valid);
    for (const value of Object.values(branchless)) {
      value.b = {};
      value.branchMap = {};
    }
    fs.writeFileSync(report, JSON.stringify(branchless));
    assert.doesNotThrow(() => verifyMobileCoverage(root));
    for (const bad of [
      {},
      Object.fromEntries(Object.keys(valid).map((key) => [key, { s: {}, f: {}, b: {} }])),
      {
        ...valid,
        [path.join(root, "src/example.ts")]: { ...valid[path.join(root, "src/example.ts")], b: { 0: [1, 0] } },
      },
    ]) {
      fs.writeFileSync(report, JSON.stringify(bad));
      assert.throws(() => verifyMobileCoverage(root));
    }
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
