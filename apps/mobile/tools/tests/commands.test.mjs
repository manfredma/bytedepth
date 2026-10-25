import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
const root = fileURLToPath(new URL("../../../../", import.meta.url));
const entry = path.join(root, "tools/node.mjs");
const command = (args, cwd = root) => spawnSync(process.execPath, [entry, ...args], { cwd, encoding: "utf8" });

test("统一入口拒绝未知命令和多余参数", () => {
  for (const args of [
    [],
    ["unknown"],
    ["assets", "mobile", "invalid"],
    ["assets", "mobile", "check", "extra"],
    ["mobile", "checks", "config"],
    ["mobile", "delivery"],
    ["mobile", "unknown"],
  ]) {
    const result = command(args);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Usage:/);
  }
});

test("移动资源入口可从包目录执行，模块导入没有读写副作用", () => {
  assert.equal(command(["assets", "mobile", "check"], path.join(root, "apps/mobile")).status, 0);
  const cwd = fs.mkdtempSync(path.join(os.tmpdir(), "bytedepth-import-"));
  try {
    const file = pathToFileURL(path.join(root, "apps/mobile/tools/assets.mjs")).href;
    const result = spawnSync(
      process.execPath,
      ["--input-type=module", "-e", `await import(${JSON.stringify(file)});`],
      { cwd, encoding: "utf8" },
    );
    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stdout, "");
    assert.deepEqual(fs.readdirSync(cwd), []);
  } finally {
    fs.rmSync(cwd, { recursive: true, force: true });
  }
});

test("fixture 失败保留非零状态，错误输出不泄露私有输入路径", () => {
  const result = command(["mobile", "fixtures", "cleanup", "--input", "/private/secret-account-password.json"]);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /private input retained/);
  assert.doesNotMatch(result.stderr, /secret-account-password/);
});

test("统一检查入口执行真实覆盖率拒绝逻辑", () => {
  const cwd = fs.mkdtempSync(path.join(os.tmpdir(), "bytedepth-coverage-"));
  try {
    const result = command(["mobile", "checks", "coverage", cwd]);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /coverage|ENOENT/);
  } finally {
    fs.rmSync(cwd, { recursive: true, force: true });
  }
});

test("自有 JS/TS 说明性注释使用中文，保留工具指令和来源链接", async () => {
  const { createRequire } = await import("node:module");
  const require = createRequire(new URL("../../package.json", import.meta.url));
  const ts = require("typescript");
  const { execFileSync } = await import("node:child_process");
  const files = execFileSync("git", ["ls-files", "*.js", "*.mjs", "*.cjs", "*.ts", "*.tsx"], {
    cwd: root,
    encoding: "utf8",
  })
    .trim()
    .split("\n");
  for (const file of files) {
    if (file.includes("/vendor/")) continue;
    const source = fs.readFileSync(path.join(root, file), "utf8");
    const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true);
    const comments = new Map();
    function visit(node) {
      for (const position of [node.pos, node.end]) {
        for (const range of [
          ...(ts.getLeadingCommentRanges(source, position) ?? []),
          ...(ts.getTrailingCommentRanges(source, position) ?? []),
        ])
          comments.set(range.pos, source.slice(range.pos, range.end));
      }
      for (const child of node.getChildren(tree)) visit(child);
    }
    visit(tree);
    for (const text of comments.values()) {
      if (!/[a-zA-Z]{3}/.test(text) || /[\u3400-\u9fff]/.test(text)) continue;
      assert.match(text, /@jest-environment|eslint-disable|https:\/\//, `${file}: ${text}`);
    }
  }
});
