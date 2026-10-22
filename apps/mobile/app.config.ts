import type { ExpoConfig } from "expo/config";
const config: ExpoConfig = {
  name: "ByteDepth 试用",
  slug: "bytedepth-reader-preview",
  version: "0.1.0",
  icon: "./assets/icon.png",
  android: { package: "cn.bytedepth.reader.preview", versionCode: 1, allowBackup: false },
  ios: { bundleIdentifier: "cn.bytedepth.reader.preview", supportsTablet: true },
  plugins: ["expo-sqlite", "expo-secure-store"],
};
export default config;
