#!/usr/bin/env node
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import path from "node:path";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const site = "bytedepth-start/src/main/resources/static/";
const mobile = "apps/mobile/src/reader/web/";
const sources = {
  template: mobile + "reader.html",
  css: mobile + "reader.css",
  theme: site + "css/theme.css",
  codeCss: site + "css/code-blocks.css",
  prismCss: site + "vendor/prism/prism.css",
  trimRange: site + "vendor/hypothesis-anchoring/trim-range.js",
  textRange: site + "vendor/hypothesis-anchoring/text-range.js",
  annotationDocument: site + "js/annotation-document.js",
  prism: site + "vendor/prism/prism-common.min.js",
  mermaid: site + "vendor/mermaid/mermaid.min.js",
  reader: mobile + "reader.js",
  codeBlocks: site + "js/code-blocks.js",
  postMermaid: site + "js/post-mermaid.js",
  hypothesisLicense: site + "vendor/hypothesis-anchoring/LICENSE",
  hypothesisNotice: site + "vendor/hypothesis-anchoring/NOTICE",
  hypothesisManifest: site + "vendor/hypothesis-anchoring/manifest.json",
  prismLicense: site + "vendor/prism/LICENSE",
  prismVersion: site + "vendor/prism/prism-version.txt",
};
const runtime = {};
const manifest = { schemaVersion: 1, generatedBy: "scripts/sync-mobile-reader-assets.mjs", sources: [] };
for (const [name, source] of Object.entries(sources)) {
  const bytes = await readFile(path.join(root, source));
  runtime[name] = bytes.toString("utf8");
  manifest.sources.push({ name, path: source, sha256: createHash("sha256").update(bytes).digest("hex"), bytes: bytes.length });
}
const target = path.join(root, "apps/mobile/assets/reader");
const check = process.argv.includes("--check");
if (!check) await mkdir(target, { recursive: true });
for (const [name, data] of [["runtime.json", runtime], ["manifest.json", manifest]]) {
  const expected = JSON.stringify(data, null, 2) + "\n";
  const file = path.join(target, name);
  if (check) {
    const actual = await readFile(file, "utf8").catch(() => null);
    if (actual !== expected) throw new Error(`Reader asset drift: ${name}; run node scripts/sync-mobile-reader-assets.mjs`);
  } else await writeFile(file, expected);
}
console.log(check ? "Reader assets match canonical sources." : "Reader assets synchronized.");
