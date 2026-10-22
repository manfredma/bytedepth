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
  // The Expo template signs release with the debug key. Only the trusted Host
  // signer may sign distributed bytes; keep debug builds usable locally.
  return `${contents.trimEnd()}\n\n// Managed preview signing happens outside the source build.\n${unsigned}\n`;
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
