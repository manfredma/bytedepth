import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import { verifyMobileConfig } from "../../verify-mobile-config.mjs";
const config = {
  name: "ByteDepth 试用",
  extra: { releaseChannel: "preview", apiOrigin: "https://staging-bytedepth.bytedepth.cn" },
  version: "0.1.0",
  android: {
    package: "cn.bytedepth.reader.preview",
    versionCode: 2,
    allowBackup: false,
    blockedPermissions: [
      "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.WRITE_EXTERNAL_STORAGE",
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.VIBRATE",
    ],
  },
  plugins: [
    "expo-sqlite",
    ["expo-secure-store", { configureAndroidBackup: false }],
    "./plugins/withLocalDataPrivacy.cjs",
    "./plugins/withPinnedAndroidBuild.cjs",
  ],
};
test("accepts preview identity and explicit privacy configuration", () =>
  assert.doesNotThrow(() => verifyMobileConfig(config)));
for (const field of ["package", "versionCode", "allowBackup", "blockedPermissions"])
  test(`rejects missing Android ${field}`, () => {
    const value = structuredClone(config);
    delete value.android[field];
    assert.throws(() => verifyMobileConfig(value));
  });
test("rejects shared backup or lost native privacy plugin", () => {
  assert.throws(() => verifyMobileConfig({ ...config, android: { ...config.android, allowBackup: true } }));
  assert.throws(() => verifyMobileConfig({ ...config, plugins: ["expo-sqlite"] }));
});
import privacy from "../../../apps/mobile/plugins/withLocalDataPrivacy.cjs";
test("excludes all local domains from cloud and device transfer", () => {
  const rules = privacy.backupRules;
  for (const mode of ["cloud-backup", "device-transfer"]) {
    const section = rules.match(new RegExp(`<${mode}>([\\s\\S]*?)</${mode}>`))[1];
    for (const domain of [
      "root",
      "file",
      "database",
      "sharedpref",
      "external",
      "device_root",
      "device_file",
      "device_database",
      "device_sharedpref",
    ])
      assert.ok(section.includes(`domain="${domain}" path="."`));
  }
});
test("native manifest explicitly disables backup and references extraction rules", () => {
  const manifest = { manifest: { application: [{ $: { "android:name": ".MainApplication" } }] } };
  privacy.applyPrivacy(manifest);
  assert.equal(manifest.manifest.application[0].$["android:allowBackup"], "false");
  assert.equal(manifest.manifest.application[0].$["android:fullBackupContent"], "false");
  assert.equal(manifest.manifest.application[0].$["android:dataExtractionRules"], "@xml/mobile_data_extraction_rules");
});
test("all native npm entry points use the dependency input lock", () => {
  const { scripts } = JSON.parse(
    fs.readFileSync(new URL("../../../apps/mobile/package.json", import.meta.url), "utf8"),
  );
  assert.equal(scripts.android, "npm run android:build --");
  for (const key of ["android:build", "prebuild:android", "ios"])
    assert.ok(scripts[key].startsWith("bash ../../scripts/with-mobile-toolchain-lock.sh "));
});

test("rejects losing pinned wrapper and managed-only release signing", () => {
  assert.throws(() =>
    verifyMobileConfig({
      ...config,
      plugins: config.plugins.filter((p) => p !== "./plugins/withPinnedAndroidBuild.cjs"),
    }),
  );
});

test("accepts the production identity only with the matching trusted origin and channel", () => {
  const production = {
    ...config,
    name: "ByteDepth",
    android: { ...config.android, package: "cn.bytedepth.reader", versionCode: 1 },
    extra: { releaseChannel: "production", apiOrigin: "https://bytedepth.cn" },
  };
  assert.doesNotThrow(() => verifyMobileConfig(production));
  assert.throws(() => verifyMobileConfig({ ...production, extra: { ...production.extra, apiOrigin: "https://staging-bytedepth.bytedepth.cn" } }));
  assert.throws(() => verifyMobileConfig({ ...production, android: config.android }));
  assert.throws(() => verifyMobileConfig({ ...production, extra: { ...production.extra, releaseChannel: "prod" } }));
});
