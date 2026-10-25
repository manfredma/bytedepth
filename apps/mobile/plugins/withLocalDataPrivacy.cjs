const { withAndroidManifest, withDangerousMod } = require("expo/config-plugins");
const fs = require("node:fs/promises");
const path = require("node:path");
const domains = [
  "root",
  "file",
  "database",
  "sharedpref",
  "external",
  "device_root",
  "device_file",
  "device_database",
  "device_sharedpref",
];
const excludes = domains.map((domain) => `    <exclude domain="${domain}" path="."/>`).join("\n");
// 仅设置 allowBackup=false 无法禁用 Android 12 及以上厂商的设备间迁移。
const backupRules = `<?xml version="1.0" encoding="utf-8"?>\n<data-extraction-rules>\n  <cloud-backup>\n${excludes}\n  </cloud-backup>\n  <device-transfer>\n${excludes}\n  </device-transfer>\n</data-extraction-rules>\n`;
function applyPrivacy(manifest) {
  const app = manifest.manifest.application[0].$;
  app["android:allowBackup"] = "false";
  app["android:fullBackupContent"] = "false";
  app["android:dataExtractionRules"] = "@xml/mobile_data_extraction_rules";
}
function withLocalDataPrivacy(config) {
  config = withAndroidManifest(config, async (mod) => {
    applyPrivacy(mod.modResults);
    return mod;
  });
  return withDangerousMod(config, [
    "android",
    async (mod) => {
      const dir = path.join(mod.modRequest.platformProjectRoot, "app/src/main/res/xml");
      await fs.mkdir(dir, { recursive: true });
      await fs.writeFile(path.join(dir, "mobile_data_extraction_rules.xml"), backupRules);
      return mod;
    },
  ]);
}
module.exports = withLocalDataPrivacy;
module.exports.backupRules = backupRules;
module.exports.applyPrivacy = applyPrivacy;
