import fs from "node:fs";
import { pathToFileURL } from "node:url";
export function verifyMobileConfig(config) {
  const android = config.android;
  if (
    config.name !== "ByteDepth 试用" ||
    typeof config.version !== "string" ||
    !/^\d+\.\d+\.\d+$/.test(config.version) ||
    android?.package !== "cn.bytedepth.reader.preview" ||
    !Number.isSafeInteger(android.versionCode) ||
    android.versionCode < 1 ||
    android.allowBackup !== false
  )
    throw new Error("Unexpected mobile preview identity or backup policy.");
  for (const permission of ["READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "SYSTEM_ALERT_WINDOW", "VIBRATE"])
    if (!android.blockedPermissions?.includes(`android.permission.${permission}`))
      throw new Error(`Unexpected Android permission: ${permission}`);
  if (
    !config.plugins?.includes("./plugins/withLocalDataPrivacy.cjs") ||
    !config.plugins.some(
      (p) => Array.isArray(p) && p[0] === "expo-secure-store" && p[1]?.configureAndroidBackup === false,
    )
  )
    throw new Error("Missing local-data privacy rules.");
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  verifyMobileConfig(JSON.parse(fs.readFileSync(process.argv[2], "utf8")));
  console.log("Mobile preview identity, permissions and backup policy verified.");
}
