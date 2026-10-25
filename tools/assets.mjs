import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { transformSync } from "rolldown/utils";
import { createRequire } from "node:module";
import crypto from "node:crypto";
const projectRoot = new URL("../", import.meta.url);
const require = createRequire(import.meta.url);

export const commit = "a7b7400d0d706d4357ec37c2a3610b25dcca89d5";
export const vendorRoot = "bytedepth-start/src/main/resources/static/vendor/hypothesis-anchoring";

/** 仅擦除类型并封装模块；产品映射规则位于 annotation-document.js。 */
export function runtimeFiles(root = vendorRoot) {
  const result = {};
  for (const name of ["trim-range", "text-range"]) {
    let source = fs.readFileSync(path.join(root, "upstream", name + ".ts"), "utf8");
    source = source
      .replace("import { trimRange } from './trim-range';", "const { trimRange } = window.HypothesisTrimRange;")
      .replaceAll("export ", "");
    const transformed = transformSync(name + ".ts", source, { lang: "ts", target: "es2022" });
    if (transformed.errors.length) throw new Error(JSON.stringify(transformed.errors));
    const namespace = name === "trim-range" ? "HypothesisTrimRange" : "HypothesisTextRange";
    const exports = name === "trim-range" ? "trimRange" : "ResolveDirection, TextPosition, TextRange";
    result[name + ".js"] =
      `// Hypothesis ${commit}. See LICENSE and NOTICE.\n(function () {\n"use strict";\n${transformed.code}\nwindow.${namespace} = Object.freeze({ ${exports} });\n})();\n`;
  }
  return result;
}

const originals = {
  LICENSE: "8000e9f55b3ea757318f072c8fae3640a4f9e2e3b578c6a271ae98b1598eee7f",
  "upstream/text-range.ts": "8846cb3f9f2b4b0606ea1e20393a07be1eab0465d49760713c8997dc0ab0ce67",
  "upstream/trim-range.ts": "779f57490be0023afda902e90c24204ab7af4a32200755a32a53975b2fb93a15",
};
const expectedFiles = [
  "LICENSE",
  "NOTICE",
  "upstream/text-range.ts",
  "upstream/trim-range.ts",
  "text-range.js",
  "trim-range.js",
  "adaptation.patch",
];
const sha256 = (value) => crypto.createHash("sha256").update(value).digest("hex");

export function verifyHypothesisSources(root = vendorRoot) {
  const manifest = JSON.parse(fs.readFileSync(path.join(root, "manifest.json"), "utf8"));
  if (
    manifest.commit !== commit ||
    JSON.stringify(manifest.files.map((file) => file.path)) !== JSON.stringify(expectedFiles)
  )
    throw new Error("Unexpected Hypothesis source manifest");
  const compiler = JSON.parse(fs.readFileSync("node_modules/rolldown/package.json", "utf8"));
  if (
    manifest.generator.version !== compiler.version ||
    manifest.generator.module !== "rolldown/utils" ||
    manifest.generator.lang !== "ts" ||
    manifest.generator.target !== "es2022"
  )
    throw new Error("Unreviewed generator version");
  for (const file of manifest.files) {
    const actual = sha256(fs.readFileSync(path.join(root, file.path)));
    if (actual !== file.sha256 || (originals[file.path] && actual !== originals[file.path]))
      throw new Error("Hypothesis integrity mismatch: " + file.path);
  }
  for (const [name, expected] of Object.entries(runtimeFiles(root))) {
    if (fs.readFileSync(path.join(root, name), "utf8") !== expected)
      throw new Error("Unrecorded runtime algorithm patch: " + name);
  }
  return true;
}

export function prism(check = false) {
  const components = require("prismjs/components.json");
  const getLoader = require("prismjs/dependencies.js");
  const root = projectRoot;
  const config = JSON.parse(fs.readFileSync(new URL("config/prism-languages.json", root), "utf8"));
  const version = require("prismjs/package.json").version;
  const component = (id) => fs.readFileSync(require.resolve(`prismjs/components/prism-${id}.min.js`), "utf8");
  // Prism 解析器也会排序可选依赖及语法修改，例如 PHP/markup。
  const ids = getLoader(components, config.languages).getIds();
  const parts = [
    `/*! 由 python3 -m tools.bytedepth assets prism build 生成，来源 Prism ${version}（MIT），请勿编辑。 */`,
    component("core"),
    ...ids.map(component),
  ];
  for (const [alias, language] of Object.entries(config.aliases)) {
    if (!/^[a-z0-9-]+$/.test(alias) || !ids.includes(language)) {
      throw new Error(`Invalid Prism alias: ${alias} -> ${language}`);
    }
    parts.push(`Prism.languages[${JSON.stringify(alias)}] = Prism.languages[${JSON.stringify(language)}];`);
  }
  const output = `${parts.join("\n;\n")}\n`;
  const target = new URL("bytedepth-start/src/main/resources/static/vendor/prism/prism-common.min.js", root);
  if (check) {
    if (fs.readFileSync(target, "utf8") !== output) {
      throw new Error("Prism bundle is stale; run npm run build:prism");
    }
  } else {
    fs.writeFileSync(target, output);
  }
  console.log(
    `Prism ${version}: ${ids.length} grammars, ${Buffer.byteLength(output)} bytes (${fileURLToPath(target)})`,
  );
}

export function runAssets(target, action) {
  if (target === "prism") prism(action === "check");
  else if (action === "check") verifyHypothesisSources();
  else
    for (const [name, source] of Object.entries(runtimeFiles())) fs.writeFileSync(path.join(vendorRoot, name), source);
}
