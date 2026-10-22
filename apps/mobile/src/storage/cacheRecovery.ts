import type { CacheIndex } from "./CacheIndex";
/** Run before exposing the repository; immutable files precede a single index commit. */
export async function recoverCache(index: CacheIndex): Promise<void> {
  await index.recover();
}
