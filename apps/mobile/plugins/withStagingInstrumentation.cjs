const { withAppBuildGradle, withDangerousMod } = require("expo/config-plugins");
const fs = require("node:fs/promises");
const path = require("node:path");
const marker = "// ByteDepth managed staging instrumentation";
// AndroidComponents VariantOutput.versionCode is a Property<Int> in pinned AGP 9.2.
// https://developer.android.com/reference/tools/gradle-api/9.2/com/android/build/api/variant/VariantOutput
const gradle = `
${marker}
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = "cn.bytedepth.reader.preview.test"
    }
    testBuildType = "release"
    buildTypes {
        upgradeFixture {
            initWith release
            matchingFallbacks = ['release']
            signingConfig = null
            debuggable = false
        }
    }
}
androidComponents {
    onVariants(selector().withBuildType("upgradeFixture")) { variant ->
        variant.outputs.each { output -> output.versionCode.set(1) }
    }
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
`;
function instrumentationGradle(contents) {
  if (!contents.includes("android {")) throw new Error("Expected generated Android build");
  if (contents.includes(marker)) return contents;
  return `${contents.trimEnd()}\n${gradle}`;
}
function withStagingInstrumentation(config) {
  config = withAppBuildGradle(config, (mod) => {
    if (mod.modResults.language !== "groovy") throw new Error("Expected generated Groovy Android build");
    mod.modResults.contents = instrumentationGradle(mod.modResults.contents);
    return mod;
  });
  return withDangerousMod(config, [
    "android",
    async (mod) => {
      const source = await fs.readFile(
        path.join(mod.modRequest.projectRoot, "testing/android/MobileReaderStagingTest.kt"),
        "utf8",
      );
      const destination = path.join(
        mod.modRequest.platformProjectRoot,
        "app/src/androidTest/java/cn/bytedepth/reader/preview",
      );
      await fs.mkdir(destination, { recursive: true });
      await fs.writeFile(path.join(destination, "MobileReaderStagingTest.kt"), source);
      return mod;
    },
  ]);
}
module.exports = withStagingInstrumentation;
module.exports.instrumentationGradle = instrumentationGradle;
