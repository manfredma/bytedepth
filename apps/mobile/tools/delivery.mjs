import fs from "node:fs";
import { createHash } from "node:crypto";

const commit = /^[0-9a-f]{40}$/;
const sha256 = /^[0-9a-f]{64}$/;
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const expectedFields = [
  "clientCommitSha",
  "backendCandidateId",
  "backendCommitSha",
  "backendDeploymentId",
  "backendArtifactSha256",
  "certificateSha256",
];
const identityFields = ["artifactId", "apkSha256", ...expectedFields.filter((key) => key !== "certificateSha256")];
const sameVersionFields = ["applicationId", "versionName", "certificateSha256", "apiSchemaVersion", ...identityFields];
function requireValue(condition, field) {
  if (!condition) throw new Error(`Invalid mobile delivery: ${field}`);
}
function record(value, field) {
  requireValue(value !== null && typeof value === "object" && !Array.isArray(value), field);
}
function identifier(value, pattern, field) {
  requireValue(typeof value === "string" && pattern.test(value), field);
}
/** 校验标准回执结构与绑定；APK 字节和数字签名需另行验证。 */
export function validateMobileDelivery(manifest, expected) {
  record(manifest, "manifest");
  record(expected, "expected bindings");
  requireValue(manifest.schemaVersion === 1 && manifest.apiSchemaVersion === 1, "schema version");
  requireValue(manifest.artifactKind === "android-apk", "artifact kind");
  requireValue(manifest.applicationId === "cn.bytedepth.reader.preview", "application ID");
  identifier(manifest.versionName, /^\d+\.\d+\.\d+$/, "version name");
  requireValue(Number.isSafeInteger(manifest.versionCode) && manifest.versionCode > 0, "version code");
  for (const key of ["artifactId", "backendCandidateId", "backendDeploymentId"]) identifier(manifest[key], uuid, key);
  for (const key of ["clientCommitSha", "backendCommitSha"]) identifier(manifest[key], commit, key);
  for (const key of ["certificateSha256", "apkSha256", "backendArtifactSha256"]) identifier(manifest[key], sha256, key);
  for (const key of expectedFields) requireValue(expected[key] === manifest[key], `expected ${key}`);
  const download = `https://devops.bytedepth.cn/api/v1/mobile-artifacts/${manifest.artifactId}/sha256/${manifest.apkSha256}/download`;
  requireValue(manifest.immutableDownloadUrl === download, "content-addressed download");
  const receipt = manifest.publicationReceipt;
  record(receipt, "publication receipt");
  for (const key of ["id", "buildTaskId", "buildAttemptNonce"]) identifier(receipt[key], uuid, `receipt ${key}`);
  for (const key of ["artifactId", "apkSha256"]) requireValue(receipt[key] === manifest[key], `receipt ${key}`);
  requireValue(Array.isArray(manifest.mobileEvidence) && manifest.mobileEvidence.length > 0, "mobile evidence");
  for (const evidence of manifest.mobileEvidence) {
    record(evidence, "evidence entry");
    for (const key of ["id", "taskId", "attemptNonce"]) identifier(evidence[key], uuid, `evidence ${key}`);
    identifier(evidence.reportSha256, sha256, "evidence report digest");
    requireValue(evidence.result === "PASS", "evidence result");
    for (const key of identityFields) requireValue(evidence[key] === manifest[key], `evidence ${key}`);
  }
  if (expected.previous !== undefined) {
    const previous = expected.previous;
    record(previous, "previous delivery");
    requireValue(
      previous.applicationId === manifest.applicationId && previous.certificateSha256 === manifest.certificateSha256,
      "upgrade application/signing identity",
    );
    requireValue(
      Number.isSafeInteger(previous.versionCode) &&
        previous.versionCode > 0 &&
        previous.versionCode <= manifest.versionCode,
      "upgrade version code",
    );
    if (previous.versionCode === manifest.versionCode)
      for (const key of sameVersionFields) requireValue(previous[key] === manifest[key], `immutable version ${key}`);
  }
  return manifest;
}
/** 根据回执校验下载字节；包与证书的验证由 Host apksigner 完成。 */
export async function verifyMobileApkDigest(file, expectedDigest) {
  identifier(expectedDigest, sha256, "download digest");
  const info = fs.lstatSync(file);
  requireValue(info.isFile() && info.size > 0 && info.size <= 1_073_741_824, "bounded regular APK file");
  const hash = createHash("sha256");
  let bytes = 0;
  for await (const chunk of fs.createReadStream(file)) {
    bytes += chunk.length;
    requireValue(bytes <= info.size, "unchanged download size");
    hash.update(chunk);
  }
  requireValue(bytes === info.size && hash.digest("hex") === expectedDigest, "download bytes digest");
}
export async function runDelivery([manifest, expected, apk]) {
  const load = (file) => {
    requireValue(file && fs.statSync(file).size <= 2_097_152, "bounded input file");
    return JSON.parse(fs.readFileSync(file, "utf8"));
  };
  const value = validateMobileDelivery(load(manifest), load(expected));
  await verifyMobileApkDigest(apk, value.apkSha256);
  console.log(
    `Mobile receipt and download digest verified: ${value.applicationId} ${value.versionName} (${value.versionCode}), APK sha256:${value.apkSha256}`,
  );
}
