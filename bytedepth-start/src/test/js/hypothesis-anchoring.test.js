import fs from "node:fs";
import crypto from "node:crypto";
import { JSDOM } from "jsdom";
import { expect, test } from "vitest";

const root = "bytedepth-start/src/main/resources/static/vendor/hypothesis-anchoring/";
const hash = (data) => crypto.createHash("sha256").update(data).digest("hex");

test("U46_pinnedSourcesLicenseAndAdaptedFilesAreIntegrityChecked", () => {
  expect(fs.existsSync(root + "manifest.json"), "fixed upstream provenance manifest").toBe(true);
  const manifest = JSON.parse(fs.readFileSync(root + "manifest.json", "utf8"));
  expect(manifest.commit).toBe("a7b7400d0d706d4357ec37c2a3610b25dcca89d5");
  for (const file of manifest.files) expect(hash(fs.readFileSync(root + file.path))).toBe(file.sha256);
  expect(manifest.files.map((file) => file.path)).toEqual([
    "LICENSE",
    "NOTICE",
    "upstream/text-range.ts",
    "upstream/trim-range.ts",
    "text-range.js",
    "trim-range.js",
    "adaptation.patch",
  ]);
});

test("U16_upstreamPureRootOffsetsSurviveWrappingAndTrimming", () => {
  const dom = new JSDOM("<div>  a<span>😀b</span> c  </div>", { runScripts: "outside-only" });
  try {
    for (const name of ["trim-range", "text-range"]) {
      if (fs.existsSync(root + name + ".js")) dom.window.eval(fs.readFileSync(root + name + ".js", "utf8"));
    }
    expect(dom.window.HypothesisTextRange).toBeDefined();
    const { TextRange } = dom.window.HypothesisTextRange;
    const element = dom.window.document.querySelector("div");
    const range = TextRange.fromOffsets(element, 3, 6).toRange();
    expect(range.toString()).toBe("😀b");
    expect(TextRange.fromRange(range).relativeTo(element).start.offset).toBe(3);
    expect(TextRange.trimmedRange(TextRange.fromOffsets(element, 0, 10).toRange()).toString()).toBe("a😀b c");
    element.innerHTML = "  <i>a</i><strong>😀b</strong> c  ";
    expect(TextRange.fromOffsets(element, 3, 6).toRange().toString()).toBe("😀b");
  } finally {
    dom.window.close();
  }
});

test("U46_tamperedLicenseSourceRuntimeHashAndPatchAreRejected", async () => {
  const script = "tools/assets.mjs";
  expect(fs.existsSync(script), "executable vendor integrity check").toBe(true);
  const path = await import("node:path");
  const { verifyHypothesisSources } = await import(path.resolve(script));
  expect(verifyHypothesisSources()).toBe(true);
  const os = await import("node:os");
  for (const file of ["LICENSE", "upstream/text-range.ts", "text-range.js", "adaptation.patch"]) {
    const temp = fs.mkdtempSync(path.join(os.tmpdir(), "annotation-vendor-"));
    try {
      fs.cpSync(root, temp, { recursive: true });
      fs.appendFileSync(path.join(temp, file), "tampered");
      expect(() => verifyHypothesisSources(temp)).toThrow();
      fs.rmSync(path.join(temp, file));
      expect(() => verifyHypothesisSources(temp)).toThrow();
    } finally {
      fs.rmSync(temp, { recursive: true, force: true });
    }
  }
});

test("U46_changedGeneratorClaimsAndHiddenAlgorithmPatchesAreRejected", async () => {
  const path = await import("node:path");
  const os = await import("node:os");
  const { verifyHypothesisSources } = await import(path.resolve("tools/assets.mjs"));
  for (const change of [
    (m) => {
      m.commit = "main";
    },
    (m) => {
      m.generator.target = "es5";
    },
    (m) => {
      m.generator.lang = "js";
    },
    (m) => {
      m.generator.version = "0";
    },
    (m) => {
      m.generator.module = "other";
    },
    (m) => {
      m.files.pop();
    },
  ]) {
    const temp = fs.mkdtempSync(path.join(os.tmpdir(), "annotation-vendor-"));
    try {
      fs.cpSync(root, temp, { recursive: true });
      const manifest = JSON.parse(fs.readFileSync(path.join(temp, "manifest.json"), "utf8"));
      change(manifest);
      fs.writeFileSync(path.join(temp, "manifest.json"), JSON.stringify(manifest));
      expect(() => verifyHypothesisSources(temp)).toThrow();
    } finally {
      fs.rmSync(temp, { recursive: true, force: true });
    }
  }
});

test("U46_rehashingAnUnreviewedSourceOrRuntimeCannotBypassProvenance", async () => {
  const path = await import("node:path");
  const os = await import("node:os");
  const { verifyHypothesisSources } = await import(path.resolve("tools/assets.mjs"));
  for (const file of ["LICENSE", "upstream/text-range.ts", "text-range.js"]) {
    const temp = fs.mkdtempSync(path.join(os.tmpdir(), "annotation-vendor-"));
    try {
      fs.cpSync(root, temp, { recursive: true });
      fs.appendFileSync(path.join(temp, file), "\n// unreviewed change\n");
      const manifest = JSON.parse(fs.readFileSync(path.join(temp, "manifest.json"), "utf8"));
      manifest.files.find((entry) => entry.path === file).sha256 = hash(fs.readFileSync(path.join(temp, file)));
      fs.writeFileSync(path.join(temp, "manifest.json"), JSON.stringify(manifest));
      expect(() => verifyHypothesisSources(temp)).toThrow();
    } finally {
      fs.rmSync(temp, { recursive: true, force: true });
    }
  }
});
