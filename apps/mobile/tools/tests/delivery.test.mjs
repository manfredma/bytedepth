import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { Buffer } from "node:buffer";
import { validateMobileDelivery, verifyMobileApkDigest } from "../delivery.mjs";

const id = "11111111-1111-4111-8111-111111111111";
const expected = {
  clientCommitSha: "a".repeat(40),
  backendCandidateId: id,
  backendCommitSha: "b".repeat(40),
  backendDeploymentId: id,
  backendArtifactSha256: "c".repeat(64),
  certificateSha256: "d".repeat(64),
};
const { certificateSha256, ...serverAndClient } = expected;
const binding = { ...serverAndClient, artifactId: id, apkSha256: "e".repeat(64) };
const valid = {
  schemaVersion: 1,
  artifactKind: "android-apk",
  ...binding,
  certificateSha256,
  applicationId: "cn.bytedepth.reader.preview",
  versionName: "0.1.0",
  versionCode: 2,
  apiSchemaVersion: 1,
  immutableDownloadUrl: `https://devops.bytedepth.cn/api/v1/mobile-artifacts/${id}/sha256/${binding.apkSha256}/download`,
  publicationReceipt: { id, artifactId: id, apkSha256: binding.apkSha256, buildTaskId: id, buildAttemptNonce: id },
  mobileEvidence: [{ id, taskId: id, attemptNonce: id, ...binding, reportSha256: "f".repeat(64), result: "PASS" }],
};
test("accepts only the expected client, managed certificate and staging deployment bindings", () => {
  assert.equal(validateMobileDelivery(valid, expected), valid);
});
for (const field of Object.keys(valid))
  test(`rejects absent delivery ${field}`, () => {
    const value = structuredClone(valid);
    delete value[field];
    assert.throws(() => validateMobileDelivery(value, expected));
  });
test("rejects JARs, unsupported schemas, non-preview apps and incomplete APK identities", () => {
  for (const [field, value] of [
    ["artifactKind", "native-jar"],
    ["schemaVersion", 2],
    ["apiSchemaVersion", "1"],
    ["applicationId", "cn.other.reader"],
    ["versionName", "latest"],
    ["versionCode", 0],
    ["versionCode", 1.5],
    ["artifactId", "not-an-id"],
    ["clientCommitSha", "abc1234"],
    ["backendCommitSha", "b".repeat(39)],
    ["certificateSha256", "sha256:" + "d".repeat(64)],
    ["apkSha256", "E".repeat(64)],
    ["backendArtifactSha256", "c".repeat(63)],
  ])
    assert.throws(() => validateMobileDelivery({ ...valid, [field]: value }, expected));
});
test("rejects missing authoritative expected bindings and another candidate or signer", () => {
  assert.throws(() => validateMobileDelivery(valid));
  for (const field of Object.keys(expected)) {
    const absent = { ...expected };
    delete absent[field];
    assert.throws(() => validateMobileDelivery(valid, absent));
    assert.throws(() =>
      validateMobileDelivery(valid, { ...expected, [field]: "0".repeat(field.includes("Sha256") ? 64 : 40) }),
    );
  }
});
test("rejects mutable, credential-bearing or external download locations", () => {
  for (const url of [
    "https://devops.bytedepth.cn/download/latest.apk",
    valid.immutableDownloadUrl + "?token=not-a-real-token",
    valid.immutableDownloadUrl + "#latest",
    valid.immutableDownloadUrl.replace("https:", "http:"),
    valid.immutableDownloadUrl.replace("devops.bytedepth.cn", "other.example"),
    valid.immutableDownloadUrl.replace("https://", "https://user:password@"),
    valid.immutableDownloadUrl.replace(binding.apkSha256, "0".repeat(64)),
  ])
    assert.throws(() => validateMobileDelivery({ ...valid, immutableDownloadUrl: url }, expected));
});
test("requires a publication receipt with the exact persisted APK identity and build attempt", () => {
  for (const field of ["id", "artifactId", "apkSha256", "buildTaskId", "buildAttemptNonce"]) {
    const value = structuredClone(valid);
    delete value.publicationReceipt[field];
    assert.throws(() => validateMobileDelivery(value, expected));
  }
  for (const field of ["artifactId", "apkSha256"]) {
    const value = structuredClone(valid);
    value.publicationReceipt[field] = field === "artifactId" ? "22222222-2222-4222-8222-222222222222" : "0".repeat(64);
    assert.throws(() => validateMobileDelivery(value, expected));
  }
});
test("rejects missing, failed or differently bound mobile evidence", () => {
  assert.throws(() => validateMobileDelivery({ ...valid, mobileEvidence: [] }, expected));
  for (const field of Object.keys(valid.mobileEvidence[0])) {
    const value = structuredClone(valid);
    delete value.mobileEvidence[0][field];
    assert.throws(() => validateMobileDelivery(value, expected));
  }
  for (const field of Object.keys(binding)) {
    const value = structuredClone(valid);
    value.mobileEvidence[0][field] = field.endsWith("Id")
      ? "22222222-2222-4222-8222-222222222222"
      : "0".repeat(field.includes("Sha256") ? 64 : 40);
    assert.throws(() => validateMobileDelivery(value, expected));
  }
  const failed = structuredClone(valid);
  failed.mobileEvidence[0].result = "FAIL";
  assert.throws(() => validateMobileDelivery(failed, expected));
});
test("upgrade preserves the signing certificate and cannot overwrite a version identity", () => {
  const previous = { ...valid, versionCode: 1 };
  assert.equal(validateMobileDelivery(valid, { ...expected, previous }), valid);
  assert.throws(() =>
    validateMobileDelivery(valid, { ...expected, previous: { ...previous, certificateSha256: "0".repeat(64) } }),
  );
  assert.throws(() =>
    validateMobileDelivery(valid, { ...expected, previous: { ...previous, applicationId: "cn.other.reader" } }),
  );
  assert.throws(() => validateMobileDelivery(valid, { ...expected, previous: { ...previous, versionCode: 3 } }));
  assert.equal(validateMobileDelivery(valid, { ...expected, previous: valid }), valid);
  for (const field of ["apkSha256", "versionName", "clientCommitSha", "backendDeploymentId"])
    assert.throws(() => validateMobileDelivery(valid, { ...expected, previous: { ...valid, [field]: "different" } }));
});
test("CLI requires bounded JSON inputs and reports only verified public receipt identity", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "mobile-delivery-"));
  try {
    const manifest = path.join(dir, "manifest.json");
    const bindings = path.join(dir, "expected.json");
    const apk = path.join(dir, "fixture-bytes.apk");
    const run = (...args) =>
      spawnSync(process.execPath, [fileURLToPath(new URL("../delivery.mjs", import.meta.url)), ...args], {
        encoding: "utf8",
      });
    // Opaque unit fixture only; this proves byte hashing, never actual APK signing/installability.
    const bytes = Buffer.from("fixture bytes for a consumer digest test");
    const digest = createHash("sha256").update(bytes).digest("hex");
    const receipt = structuredClone(valid);
    receipt.apkSha256 = receipt.publicationReceipt.apkSha256 = receipt.mobileEvidence[0].apkSha256 = digest;
    receipt.immutableDownloadUrl = receipt.immutableDownloadUrl.replace(binding.apkSha256, digest);
    fs.writeFileSync(apk, bytes);
    fs.writeFileSync(manifest, JSON.stringify(receipt));
    fs.writeFileSync(bindings, JSON.stringify(expected));
    const green = run(manifest, bindings, apk);
    assert.equal(green.status, 0, green.stderr);
    assert.match(green.stdout, /Mobile receipt and download digest verified: cn\.bytedepth\.reader\.preview/);
    assert.notEqual(run(manifest, bindings).status, 0);
    fs.appendFileSync(apk, "tampered");
    assert.notEqual(run(manifest, bindings, apk).status, 0);
    assert.notEqual(run(manifest).status, 0);
    fs.writeFileSync(bindings, "{");
    assert.notEqual(run(manifest, bindings).status, 0);
    fs.writeFileSync(bindings, " ".repeat(2_097_153));
    assert.notEqual(run(manifest, bindings).status, 0);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
test("download verification streams bytes and rejects empty, linked, oversized or changed artifacts", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "mobile-digest-"));
  try {
    const file = path.join(dir, "artifact");
    const bytes = Buffer.alloc(256_000, 17);
    const digest = createHash("sha256").update(bytes).digest("hex");
    fs.writeFileSync(file, bytes);
    await verifyMobileApkDigest(file, digest);
    await assert.rejects(verifyMobileApkDigest(file, "0".repeat(64)));
    await assert.rejects(verifyMobileApkDigest(file, "short"));
    const link = path.join(dir, "link");
    fs.symlinkSync(file, link);
    await assert.rejects(verifyMobileApkDigest(link, digest));
    fs.truncateSync(file, 0);
    await assert.rejects(verifyMobileApkDigest(file, digest));
    fs.truncateSync(file, 1_073_741_825);
    await assert.rejects(verifyMobileApkDigest(file, digest));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
