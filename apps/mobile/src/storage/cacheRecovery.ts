import type { CacheIndex } from "./CacheIndex";
/** 对外提供仓库前执行；不可变文件写入先于单次索引提交。 */
export async function recoverCache(index: CacheIndex): Promise<void> {
  await index.recover();
}
