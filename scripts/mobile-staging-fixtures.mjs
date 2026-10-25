// release-platform 的 mobile/manifest.py 使用的固定 Node 接口。
import { runFixtures } from "../apps/mobile/tools/fixtures.mjs";
await runFixtures(process.argv.slice(2)).catch(() => {
  console.error("Managed mobile fixture operation failed; private input retained for owned cleanup");
  process.exitCode = 1;
});
