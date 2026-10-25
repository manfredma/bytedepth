// Fixed Node interface consumed by release-platform mobile/manifest.py.
import { runFixtures } from "../apps/mobile/tools/fixtures.mjs";
await runFixtures(process.argv.slice(2)).catch(() => {
  console.error("Managed mobile fixture operation failed; private input retained for owned cleanup");
  process.exitCode = 1;
});
