import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { transformSync } from "rolldown/utils";

export const commit = "a7b7400d0d706d4357ec37c2a3610b25dcca89d5";
export const vendorRoot = "bytedepth-start/src/main/resources/static/vendor/hypothesis-anchoring";

/** Type erasure and module wrapping only. Product mapping lives in annotation-document.js. */
export function runtimeFiles(root = vendorRoot) {
  const result = {};
  for (const name of ["trim-range", "text-range"]) {
    let source = fs.readFileSync(path.join(root, "upstream", name + ".ts"), "utf8");
    source = source.replace("import { trimRange } from './trim-range';", "const { trimRange } = window.HypothesisTrimRange;").replaceAll("export ", "");
    const transformed = transformSync(name + ".ts", source, { lang: "ts", target: "es2022" });
    if (transformed.errors.length) throw new Error(JSON.stringify(transformed.errors));
    const namespace = name === "trim-range" ? "HypothesisTrimRange" : "HypothesisTextRange";
    const exports = name === "trim-range" ? "trimRange" : "ResolveDirection, TextPosition, TextRange";
    result[name + ".js"] = `// Hypothesis ${commit}. See LICENSE and NOTICE.\n(function () {\n"use strict";\n${transformed.code}\nwindow.${namespace} = Object.freeze({ ${exports} });\n})();\n`;
  }
  return result;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  for (const [name, source] of Object.entries(runtimeFiles())) fs.writeFileSync(path.join(vendorRoot, name), source);
}
