import packageManifest from "./package.json";
import type { ExpoConfig } from "expo/config";
import { mobileRelease } from "./src/releaseProfile";
const release = mobileRelease(process.env.EXPO_PUBLIC_RELEASE_CHANNEL);
const config: ExpoConfig = {
  name: release.name,
  slug: "bytedepth-reader-preview",
  version: packageManifest.version,
  icon: "./assets/icon.png",
  android: {
    package: release.applicationId,
    versionCode: release.versionCode,
    allowBackup: false,
    blockedPermissions: [
      "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.WRITE_EXTERNAL_STORAGE",
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.VIBRATE",
    ],
  },
  ios: { bundleIdentifier: release.applicationId, supportsTablet: true },
  extra: { releaseChannel: release.channel, apiOrigin: release.origin },
  plugins: [
    "expo-sqlite",
    ["expo-secure-store", { configureAndroidBackup: false }],
    "./plugins/withLocalDataPrivacy.cjs",
    "./plugins/withPinnedAndroidBuild.cjs",
    ...(release.channel === "preview" ? ["./plugins/withStagingInstrumentation.cjs"] : []),
  ],
};
export default config;
