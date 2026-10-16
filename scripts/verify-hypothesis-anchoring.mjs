import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import { commit, runtimeFiles, vendorRoot } from "./build-hypothesis-anchoring.mjs";

const originals = {
  "LICENSE": "8000e9f55b3ea757318f072c8fae3640a4f9e2e3b578c6a271ae98b1598eee7f",
  "upstream/text-range.ts": "8846cb3f9f2b4b0606ea1e20393a07be1eab0465d49760713c8997dc0ab0ce67",
  "upstream/trim-range.ts": "779f57490be0023afda902e90c24204ab7af4a32200755a32a53975b2fb93a15"
};
const expectedFiles = ["LICENSE", "NOTICE", "upstream/text-range.ts", "upstream/trim-range.ts", "text-range.js", "trim-range.js", "adaptation.patch"];
const sha256 = (value) => crypto.createHash("sha256").update(value).digest("hex");

export function verifyHypothesisSources(root = vendorRoot) {
  const manifest = JSON.parse(fs.readFileSync(path.join(root, "manifest.json"), "utf8"));
  if (manifest.commit !== commit || JSON.stringify(manifest.files.map((file) => file.path)) !== JSON.stringify(expectedFiles)) throw new Error("Unexpected Hypothesis source manifest");
  const compiler = JSON.parse(fs.readFileSync("node_modules/rolldown/package.json", "utf8"));
  if (manifest.generator.version !== compiler.version || manifest.generator.module !== "rolldown/utils" || manifest.generator.lang !== "ts" || manifest.generator.target !== "es2022") throw new Error("Unreviewed generator version");
  for (const file of manifest.files) {
    const actual = sha256(fs.readFileSync(path.join(root, file.path)));
    if (actual !== file.sha256 || (originals[file.path] && actual !== originals[file.path])) throw new Error("Hypothesis integrity mismatch: " + file.path);
  }
  for (const [name, expected] of Object.entries(runtimeFiles(root))) {
    if (fs.readFileSync(path.join(root, name), "utf8") !== expected) throw new Error("Unrecorded runtime algorithm patch: " + name);
  }
  return true;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  verifyHypothesisSources(process.argv[2]);
  process.stdout.write("Pinned Hypothesis source integrity verified.\n");
}
