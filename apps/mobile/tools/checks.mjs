import fs from "node:fs";
import path from "node:path";
import { mobileRelease } from "../src/releaseProfile.js";
export function verifyMobileConfig(config) {
  const release = mobileRelease(config.extra?.releaseChannel);
  const android = config.android;
  if (
    config.name !== release.name ||
    config.extra?.apiOrigin !== release.origin ||
    typeof config.version !== "string" ||
    !/^\d+\.\d+\.\d+$/.test(config.version) ||
    android?.package !== release.applicationId ||
    !Number.isSafeInteger(android.versionCode) ||
    android.versionCode < 1 ||
    android.allowBackup !== false
  )
    throw new Error("Unexpected mobile identity or backup policy.");
  for (const permission of ["READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "SYSTEM_ALERT_WINDOW", "VIBRATE"])
    if (!android.blockedPermissions?.includes(`android.permission.${permission}`))
      throw new Error(`Unexpected Android permission: ${permission}`);
  if (
    !config.plugins?.includes("./plugins/withLocalDataPrivacy.cjs") ||
    !config.plugins.includes("./plugins/withPinnedAndroidBuild.cjs") ||
    !config.plugins.some(
      (p) => Array.isArray(p) && p[0] === "expo-secure-store" && p[1]?.configureAndroidBackup === false,
    )
  )
    throw new Error("Missing local-data privacy rules.");
}
export function verifyMobileCoverage(directory) {
  const root = path.resolve(directory);
  const report = JSON.parse(fs.readFileSync(path.join(root, "coverage/coverage-final.json"), "utf8"));
  const source = [];
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name === "__tests__") continue;
      const file = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(file);
      else if (/\.(?:ts|tsx|js|cjs)$/.test(entry.name)) source.push(file);
    }
  }
  walk(path.join(root, "src"));
  walk(path.join(root, "plugins"));
  source.push(path.join(root, "App.tsx"));
  for (const file of source) {
    const covered = report[file];
    if (!covered) throw new Error(`Missing mobile coverage: ${path.relative(root, file)}`);
    for (const [metric, mapping] of [
      ["s", "statementMap"],
      ["f", "fnMap"],
      ["b", "branchMap"],
    ]) {
      const counts = covered[metric];
      const locations = covered[mapping];
      if (
        !counts ||
        !locations ||
        JSON.stringify(Object.keys(counts).sort()) !== JSON.stringify(Object.keys(locations).sort())
      )
        throw new Error(`Missing or inconsistent mobile ${metric}: ${path.relative(root, file)}`);
      if (metric === "b" && Object.values(counts).some((value) => !Array.isArray(value) || !value.length))
        throw new Error(`Empty branch coverage: ${path.relative(root, file)}`);
      const values = Object.values(counts).flat();
      if (values.some((count) => !Number.isFinite(count) || count < 1))
        throw new Error(`Uncovered mobile ${metric}: ${path.relative(root, file)}`);
    }
  }
  console.log(`Mobile owned source coverage verified: ${source.length} files, 100% statements/branches/functions.`);
}
