const { withAppBuildGradle, withDangerousMod } = require("expo/config-plugins");
const fs = require("node:fs/promises");
const path = require("node:path");
const toolchain = require("../android-toolchain.json");
const unsigned = "android.buildTypes.release.signingConfig = null";
function pinWrapper(contents) {
  const urls = contents.match(/^distributionUrl=.*$/gm);
  const expected = `distributionUrl=https\\://services.gradle.org/distributions/gradle-${toolchain.gradleVersion}-bin.zip`;
  if (!urls || urls.length !== 1 || urls[0] !== expected) throw new Error("Unexpected Gradle wrapper");
  const checksums = contents.match(/^distributionSha256Sum=.*$/gm);
  const checksum = `distributionSha256Sum=${toolchain.gradleDistributionSha256}`;
  if (checksums) {
    if (checksums.length !== 1 || checksums[0] !== checksum) throw new Error("Unexpected Gradle checksum");
    return contents;
  }
  return `${contents.trimEnd()}\n${checksum}\n`;
}
function unsignedRelease(contents) {
  if (contents.includes(unsigned)) return contents;
  // Expo 模板使用调试密钥签署发布包；分发包只允许由可信 Host
  // 签名器签署，同时保留本地调试构建能力。
  return `${contents.trimEnd()}\n\n// 签名在源码构建之外执行；preview 和 production 保持相同的已验证运行时。
// R8 优化仍暂缓启用。\n${unsigned}\nandroid.buildTypes.release.minifyEnabled = false\nandroid.buildTypes.release.shrinkResources = false\n`;
}
function withPinnedAndroidBuild(config) {
  config = withAppBuildGradle(config, (mod) => {
    if (mod.modResults.language !== "groovy") throw new Error("Expected generated Groovy Android build");
    mod.modResults.contents = unsignedRelease(mod.modResults.contents);
    return mod;
  });
  return withDangerousMod(config, [
    "android",
    async (mod) => {
      const file = path.join(mod.modRequest.platformProjectRoot, "gradle/wrapper/gradle-wrapper.properties");
      const contents = await fs.readFile(file, "utf8");
      await fs.writeFile(file, pinWrapper(contents));
      return mod;
    },
  ]);
}
module.exports = withPinnedAndroidBuild;
module.exports.pinWrapper = pinWrapper;
module.exports.unsignedRelease = unsignedRelease;
