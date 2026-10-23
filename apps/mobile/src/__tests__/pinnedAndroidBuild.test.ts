const mockReadFile = jest.fn();
const mockWriteFile = jest.fn();
jest.mock("node:fs/promises", () => ({
  readFile: (...args: unknown[]) => mockReadFile(...args),
  writeFile: (...args: unknown[]) => mockWriteFile(...args),
}));
jest.mock("expo/config-plugins", () => ({
  withAppBuildGradle: (config: Record<string, unknown>, callback: unknown) => ({ ...config, gradleCallback: callback }),
  withDangerousMod: (config: Record<string, unknown>, [, callback]: [string, unknown]) => ({
    ...config,
    dangerousCallback: callback,
  }),
}));
const original =
  "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.4.1-bin.zip\nnetworkTimeout=10000\n";
const toolchain = require("../../android-toolchain.json");
const plugin = jest.requireActual("../../plugins/withPinnedAndroidBuild.cjs");
it("pins the original generated wrapper checksum idempotently", () => {
  const pinned = plugin.pinWrapper(original);
  expect(pinned).toContain(`distributionSha256Sum=${toolchain.gradleDistributionSha256}\n`);
  expect(plugin.pinWrapper(pinned)).toBe(pinned);
  expect(() => plugin.pinWrapper(original.replace("9.4.1", "9.8.1"))).toThrow("Unexpected Gradle wrapper");
  expect(() => plugin.pinWrapper(original + "distributionSha256Sum=" + "f".repeat(64) + "\n")).toThrow("checksum");
  expect(() => plugin.pinWrapper(original + original)).toThrow("Unexpected Gradle wrapper");
  expect(() => plugin.pinWrapper("networkTimeout=10000\n")).toThrow("Unexpected Gradle wrapper");
  const checksum = `distributionSha256Sum=${toolchain.gradleDistributionSha256}\n`;
  expect(() => plugin.pinWrapper(original + checksum + checksum)).toThrow("checksum");
});
it("overrides the Expo template release debug signing without changing debug builds", () => {
  const gradle =
    "android { buildTypes { debug { signingConfig signingConfigs.debug } release { signingConfig signingConfigs.debug } } }";
  const result = plugin.unsignedRelease(gradle);
  expect(result).toContain("android.buildTypes.release.signingConfig = null");
  expect(result).toContain("android.buildTypes.release.minifyEnabled = false");
  expect(result).toContain("android.buildTypes.release.shrinkResources = false");
  expect(result).toContain("debug { signingConfig signingConfigs.debug }");
  expect(plugin.unsignedRelease(result)).toBe(result);
});
it("applies the pinned wrapper and unsigned release through real plugin callbacks", async () => {
  const config = plugin({});
  const mod = { modResults: { language: "groovy", contents: "android {}\n" } };
  expect(config.gradleCallback(mod)).toBe(mod);
  expect(mod.modResults.contents).toContain("android.buildTypes.release.signingConfig = null");
  expect(() => config.gradleCallback({ modResults: { language: "kotlin" } })).toThrow("Groovy");
  mockReadFile.mockResolvedValue(original);
  const dangerous = { modRequest: { platformProjectRoot: "/isolated/android" } };
  expect(await config.dangerousCallback(dangerous)).toBe(dangerous);
  expect(mockReadFile).toHaveBeenCalledWith("/isolated/android/gradle/wrapper/gradle-wrapper.properties", "utf8");
  expect(mockWriteFile).toHaveBeenCalledWith(
    "/isolated/android/gradle/wrapper/gradle-wrapper.properties",
    plugin.pinWrapper(original),
  );
});
