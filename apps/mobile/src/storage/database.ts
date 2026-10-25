import type { SQLiteDatabase } from "expo-sqlite";
import type { ResumePosition } from "../contracts/mobile";
export interface StoredCopy {
  representationHash: string;
  body: string;
  assets: Record<string, { file: string; mediaType: string }>;
  lastRead: string;
  lastConfirmedAt: string | null;
}
export interface CacheData {
  copies: Record<string, StoredCopy>;
  generations: Record<string, number>;
  unavailable: Record<string, boolean>;
}
export interface HistoryRow {
  postId: string;
  title: string;
  slug: string;
  position: ResumePosition | null;
  updatedAt: string;
}
export interface LocalDatabase {
  readCache(): Promise<CacheData>;
  changeCache<T>(change: (data: CacheData) => T): Promise<T>;
  history(): Promise<HistoryRow[]>;
  changeHistory(postId: string, change: (row: HistoryRow | null) => HistoryRow): Promise<void>;
}
export const emptyCache = (): CacheData => ({ copies: {}, generations: {}, unavailable: {} });

/** 版本化迁移不得清除较新应用创建的数据库。 */
export class SqliteLocalDatabase implements LocalDatabase {
  private tail: Promise<unknown> = Promise.resolve();
  constructor(private readonly sqlite: SQLiteDatabase) {}
  private serial<T>(action: () => Promise<T>): Promise<T> {
    const result = this.tail.then(action);
    this.tail = result.catch(() => undefined);
    return result;
  }
  async initialize(): Promise<void> {
    const row = await this.sqlite.getFirstAsync<{ user_version: number }>("PRAGMA user_version");
    const version = row?.user_version ?? 0;
    if (version > 1) throw new Error("Local database schema is newer than this application");
    await this.sqlite.execAsync("PRAGMA journal_mode = WAL; PRAGMA synchronous = FULL;");
    if (version === 0)
      await this.sqlite.withExclusiveTransactionAsync(async (tx) => {
        await tx.execAsync(
          "CREATE TABLE cache_index (id INTEGER PRIMARY KEY CHECK(id = 1), value TEXT NOT NULL); CREATE TABLE local_history (post_id TEXT PRIMARY KEY, value TEXT NOT NULL); PRAGMA user_version = 1;",
        );
        await tx.runAsync("INSERT INTO cache_index (id, value) VALUES (1, ?)", JSON.stringify(emptyCache()));
      });
  }
  async readCache(): Promise<CacheData> {
    const row = await this.sqlite.getFirstAsync<{ value: string }>("SELECT value FROM cache_index WHERE id = 1");
    if (!row) throw new Error("Missing cache index");
    return JSON.parse(row.value) as CacheData;
  }
  async changeCache<T>(change: (data: CacheData) => T): Promise<T> {
    return this.serial(async () => {
      let result!: T;
      await this.sqlite.withExclusiveTransactionAsync(async (tx) => {
        const row = await tx.getFirstAsync<{ value: string }>("SELECT value FROM cache_index WHERE id = 1");
        if (!row) throw new Error("Missing cache index");
        const data = JSON.parse(row.value) as CacheData;
        result = change(data);
        await tx.runAsync("UPDATE cache_index SET value = ? WHERE id = 1", JSON.stringify(data));
      });
      return result;
    });
  }
  async history(): Promise<HistoryRow[]> {
    return (await this.sqlite.getAllAsync<{ value: string }>("SELECT value FROM local_history")).map(
      (row) => JSON.parse(row.value) as HistoryRow,
    );
  }
  async changeHistory(postId: string, change: (row: HistoryRow | null) => HistoryRow): Promise<void> {
    return this.serial(async () => {
      await this.sqlite.withExclusiveTransactionAsync(async (tx) => {
        const row = await tx.getFirstAsync<{ value: string }>(
          "SELECT value FROM local_history WHERE post_id = ?",
          postId,
        );
        const next = change(row ? (JSON.parse(row.value) as HistoryRow) : null);
        await tx.runAsync(
          "INSERT INTO local_history (post_id, value) VALUES (?, ?) ON CONFLICT(post_id) DO UPDATE SET value = excluded.value",
          postId,
          JSON.stringify(next),
        );
      });
    });
  }
}
/** Android 应用配置禁用此持久化本地数据库的云备份和设备迁移。 */
export async function openLocalDatabase(): Promise<LocalDatabase> {
  // eslint-disable-next-line @typescript-eslint/no-require-imports -- Metro 按需加载原生边界；注入的存储不需要原生运行时。
  const { openDatabaseAsync }: typeof import("expo-sqlite") = require("expo-sqlite");
  // eslint-disable-next-line @typescript-eslint/no-require-imports -- 沿用相同的原生按需加载边界。
  const { Directory, Paths }: typeof import("expo-file-system") = require("expo-file-system");
  const directory = new Directory(Paths.document, "mobile-local");
  directory.create({ idempotent: true, intermediates: true });
  const database = new SqliteLocalDatabase(await openDatabaseAsync("reader.sqlite", {}, directory.uri));
  await database.initialize();
  return database;
}
