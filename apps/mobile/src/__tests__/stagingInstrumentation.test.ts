const mockReadFile = jest.fn();
const mockMkdir = jest.fn();
const mockWriteFile = jest.fn();
jest.mock("node:fs/promises", () => ({
  readFile: (...args: unknown[]) => mockReadFile(...args),
  mkdir: (...args: unknown[]) => mockMkdir(...args),
  writeFile: (...args: unknown[]) => mockWriteFile(...args),
}));
jest.mock("expo/config-plugins", () => ({
  withAppBuildGradle: (config: Record<string, unknown>, callback: unknown) => ({ ...config, gradleCallback: callback }),
  withDangerousMod: (config: Record<string, unknown>, [, callback]: [string, unknown]) => ({
    ...config,
    dangerousCallback: callback,
  }),
}));
const plugin = jest.requireActual("../../plugins/withStagingInstrumentation.cjs");
it("adds only test dependencies and freezes the unsigned offline upgrade fixture", () => {
  const result = plugin.instrumentationGradle("android {}\nreact {}\n");
  expect(result).toContain('testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"');
  expect(result).toContain('testBuildType = "release"');
  expect(result).toContain('androidTestImplementation("androidx.test:runner:1.6.2")');
  expect(result).toContain('androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")');
  expect(result).toContain("upgradeFixture {");
  expect(result).toContain("initWith release");
  expect(result).toContain("signingConfig = null");
  expect(result).toContain("debuggable = false");
  expect(result).toContain("output.versionCode.set(1)");
  expect(result).toContain('selector().withBuildType("upgradeFixture")');
  expect(result).not.toContain("debuggableVariants");
  expect(plugin.instrumentationGradle(result)).toBe(result);
  expect(() => plugin.instrumentationGradle("// broken template")).toThrow("Android");
});
it("copies reviewed Kotlin only into androidTest and applies the generated Groovy mod", async () => {
  const config = plugin({});
  const mod = { modResults: { language: "groovy", contents: "android {}\nreact {}\n" } };
  expect(config.gradleCallback(mod)).toBe(mod);
  expect(mod.modResults.contents).toContain("output.versionCode.set(1)");
  expect(() => config.gradleCallback({ modResults: { language: "kotlin" } })).toThrow("Groovy");
  mockReadFile.mockResolvedValue("reviewed Kotlin");
  const dangerous = { modRequest: { projectRoot: "/source/mobile", platformProjectRoot: "/build/android" } };
  expect(await config.dangerousCallback(dangerous)).toBe(dangerous);
  expect(mockReadFile).toHaveBeenCalledWith("/source/mobile/testing/android/MobileReaderStagingTest.kt", "utf8");
  expect(mockMkdir).toHaveBeenCalledWith("/build/android/app/src/androidTest/java/cn/bytedepth/reader/preview", {
    recursive: true,
  });
  expect(mockWriteFile).toHaveBeenCalledWith(
    "/build/android/app/src/androidTest/java/cn/bytedepth/reader/preview/MobileReaderStagingTest.kt",
    "reviewed Kotlin",
  );
});

it("keeps trial release classes available to the separately built instrumentation APK", () => {
  const pinned = jest.requireActual("../../plugins/withPinnedAndroidBuild.cjs");
  const result = pinned.unsignedRelease(plugin.instrumentationGradle("android {}"));
  expect(result).toContain("android.buildTypes.release.minifyEnabled = false");
  expect(result).toContain("android.buildTypes.release.shrinkResources = false");
  expect(result).toContain("debuggable = false");
  expect(result).not.toContain("staging-instrumentation.pro");
});
