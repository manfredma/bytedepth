import fs from "node:fs";

// Node 依赖按职责加载，移动检查不依赖根目录的前端构建工具。
const usage = () => {
  throw new Error(
    "Usage: node tools/node.mjs assets prism|hypothesis|mobile build|check | mobile checks config|coverage PATH | mobile delivery MANIFEST EXPECTED APK | mobile fixtures prepare|cleanup FLAGS",
  );
};
const [group, target, action, ...args] = process.argv.slice(2);
try {
  if (group === "assets") {
    if (!["prism", "hypothesis", "mobile"].includes(target) || !["build", "check"].includes(action) || args.length)
      usage();
    if (target === "mobile") await (await import("../apps/mobile/tools/assets.mjs")).readerAssets(action === "check");
    else await (await import("./assets.mjs")).runAssets(target, action);
  } else if (group === "mobile") {
    if (target === "checks") {
      if (!["config", "coverage"].includes(action) || args.length !== 1) usage();
      const checks = await import("../apps/mobile/tools/checks.mjs");
      if (action === "coverage") checks.verifyMobileCoverage(args[0]);
      else checks.verifyMobileConfig(JSON.parse(fs.readFileSync(args[0], "utf8")));
    } else if (target === "delivery") {
      if (!action || args.length !== 2) usage();
      await (await import("../apps/mobile/tools/delivery.mjs")).runDelivery([action, ...args]);
    } else if (target === "fixtures") {
      // fixture 错误可能包含账号、私有路径或服务响应，只输出固定诊断。
      try {
        await (await import("../apps/mobile/tools/fixtures.mjs")).runFixtures([action, ...args]);
      } catch {
        throw new Error("Managed mobile fixture operation failed; private input retained for owned cleanup");
      }
    } else usage();
  } else usage();
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
