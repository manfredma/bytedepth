import packageManifest from "./package.json";
import type { ExpoConfig } from "expo/config";
const config: ExpoConfig = {
  name: "ByteDepth 试用",
  slug: "bytedepth-reader-preview",
  version: packageManifest.version,
  icon: "./assets/icon.png",
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
  ios: { bundleIdentifier: "cn.bytedepth.reader.preview", supportsTablet: true },
  plugins: [
    "expo-sqlite",
    ["expo-secure-store", { configureAndroidBackup: false }],
    "./plugins/withLocalDataPrivacy.cjs",
    "./plugins/withPinnedAndroidBuild.cjs",
    "./plugins/withStagingInstrumentation.cjs",
  ],
};
export default config;
