import type { SQLiteDatabase } from "expo-sqlite";
import type { Directory, File } from "expo-file-system";
import { SqliteLocalDatabase, emptyCache, openLocalDatabase } from "../database";
import { NativeFileStore, createNativeFileStore } from "../FileStore";
import { SqliteDriver, driver, TestDirectory, TestFile } from "./nativeFixtures";
import { content, now, MemoryDatabase, MemoryFiles, hash } from "./fixtures";
import { HistoryStore } from "../HistoryStore";
import { recoverCache } from "../cacheRecovery";
import { CacheIndex } from "../CacheIndex";
import { createNativeReaderRepository } from "../../reader/ReaderRepository";
jest.mock("expo-file-system", () => jest.requireActual("./nativeFixtures").nativeFs);
jest.mock("expo-sqlite", () => ({ openDatabaseAsync: async () => jest.requireActual("./nativeFixtures").driver }));
jest.mock("../../auth/nativeCredentials", () => ({
  sha256: async (bytes: Uint8Array) => jest.requireActual("./fixtures").hash(bytes),
}));
const adapter = (db: SqliteDriver) => new SqliteLocalDatabase(db as unknown as SQLiteDatabase);
describe("native adapter boundary", () => {
  beforeEach(() => {
    driver.reset();
    TestFile.values.clear();
  });
  test("v1 migration initializes persistent index and refuses downgrade without erasing it", async () => {
    const db = new SqliteDriver(),
      store = adapter(db);
    await store.initialize();
    expect(await store.readCache()).toEqual(emptyCache());
    await store.changeCache((data) => {
      data.unavailable["1"] = true;
    });
    await store.initialize();
    expect((await store.readCache()).unavailable["1"]).toBe(true);
    db.version = 2;
    await expect(store.initialize()).rejects.toThrow("newer");
    expect((await store.readCache()).unavailable["1"]).toBe(true);
  });
  test("an interrupted SQLite commit rolls back and later writes recover", async () => {
    const db = new SqliteDriver(),
      store = adapter(db);
    await store.initialize();
    db.failCommit = true;
    await expect(
      store.changeCache((data) => {
        data.unavailable["1"] = true;
      }),
    ).rejects.toThrow();
    expect((await store.readCache()).unavailable).toEqual({});
    db.failCommit = false;
    await store.changeCache((data) => {
      data.generations["1"] = 2;
    });
    expect((await store.readCache()).generations["1"]).toBe(2);
  });
  test("history and cache transactions are independent and position preserves exact code points", async () => {
    const db = new SqliteDriver(),
      store = adapter(db);
    await store.initialize();
    const history = new HistoryStore(store);
    await history.record(content(), now);
    const position = {
      postId: "1",
      contentVersion: 1,
      projectionVersion: 1,
      segmentId: "p1",
      codePointOffset: 2,
      blockKind: "paragraph",
      blockText: "a😀b",
      precedingHeadings: ["Heading"],
      viewportRatio: 0.25,
      progress: 0.5,
      updatedAt: now,
    };
    await history.save(position);
    await store.changeCache((data) => {
      delete data.copies["1"];
    });
    await history.record(content("1", 2), now);
    expect((await history.list())[0].position).toEqual(position);
    expect((await store.readCache()).copies).toEqual({});
  });
  test("missing index is an error and never silently resets local data", async () => {
    const db = new SqliteDriver();
    db.version = 1;
    const store = adapter(db);
    await expect(store.readCache()).rejects.toThrow("Missing");
    await expect(store.changeCache(() => undefined)).rejects.toThrow("Missing");
  });
  test("native file adapter writes bytes, atomically renames and rejects traversal", async () => {
    const files = new NativeFileStore(new TestDirectory() as unknown as Directory, TestFile as unknown as typeof File);
    const name = `a-${"a".repeat(64)}`;
    await files.write("stage-1", new Uint8Array([1, 2, 3]));
    await files.move("stage-1", name);
    expect(await files.read(name)).toEqual(new Uint8Array([1, 2, 3]));
    expect(await files.list()).toEqual([{ name, size: 3 }]);
    expect(files.uri(name)).toBe(`file:///local/${name}`);
    await expect(files.read("../private")).rejects.toThrow("Invalid");
    await files.remove(name);
    await files.remove(name);
    expect(await files.list()).toEqual([]);
  });
  test("native factories initialize recovery and return the concrete repository", async () => {
    const db = await openLocalDatabase();
    expect(await db.readCache()).toEqual(emptyCache());
    const files = await createNativeFileStore();
    expect(await files.list()).toEqual([]);
    const repo = await createNativeReaderRepository(
      {
        async request() {
          throw new Error("offline");
        },
      },
      {
        async download() {
          throw new Error("offline");
        },
      },
    );
    expect(await repo.listRecent()).toEqual([]);
    expect(await repo.assetDataUris(content())).toEqual({});
    const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash);
    await recoverCache(index);
    expect(await index.get("1")).toBeNull();
  });
  test("invalid positions and positions for unseen articles never create history", async () => {
    const history = new HistoryStore(new MemoryDatabase());
    const position = {
      postId: "1",
      contentVersion: 1,
      projectionVersion: 1,
      segmentId: "p1",
      codePointOffset: 0,
      blockKind: "paragraph",
      blockText: "Hello",
      precedingHeadings: [],
      viewportRatio: 0,
      progress: 0,
      updatedAt: now,
    };
    await expect(history.save(position)).rejects.toThrow("not been opened");
    await expect(history.save({ ...position, codePointOffset: 99 })).rejects.toThrow();
    expect(await history.list()).toEqual([]);
  });
});

describe("native decoder contract", () => {
  beforeEach(() => {
    driver.reset();
    TestFile.values.clear();
  });
  test.each([
    { width: 1, height: 1 },
    { width: 0, height: 1 },
    { width: 1, height: 0 },
    { width: Number.NaN, height: 1 },
    { width: 1, height: Number.NaN },
  ])("decoded dimensions %j gate persistence", async (dimensions) => {
    const { Image } = jest.requireActual<typeof import("react-native")>("react-native");
    const decode = jest.spyOn(Image, "getSize").mockImplementation(() => Promise.resolve(dimensions));
    try {
      const c = content("1", 1, true),
        bytes = new Uint8Array([1]);
      const repo = await createNativeReaderRepository(
        {
          async request<T>(path: string) {
            const { postId, contentVersion, projectionVersion, digest, representationHash, shellVersion } = c;
            return (
              path.endsWith("/state")
                ? {
                    schemaVersion: 1,
                    postId,
                    availability: "AVAILABLE",
                    identity: { postId, contentVersion, projectionVersion, digest, representationHash, shellVersion },
                  }
                : c
            ) as T;
          },
        },
        {
          async download() {
            return { key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) };
          },
        },
      );
      await repo.refresh("1");
      expect((await repo.dependencies.index.get("1")) !== null).toBe(dimensions.width > 0 && dimensions.height > 0);
      repo.release("1");
    } finally {
      decode.mockRestore();
    }
  });
  test("unexpected subdirectories are rejected instead of counted as zero bytes", async () => {
    const directory = new TestDirectory();
    directory.list = () => [new TestDirectory()] as unknown as TestFile[];
    const files = new NativeFileStore(directory as unknown as Directory, TestFile as unknown as typeof File);
    await expect(files.list()).rejects.toThrow("Unexpected");
  });
  test("missing schema-version row initializes v1 without affecting history", async () => {
    const db = new SqliteDriver(),
      read = db.getFirstAsync.bind(db);
    db.getFirstAsync = async <T>(sql: string, id?: string) => (sql.includes("PRAGMA") ? null : read<T>(sql, id));
    const store = adapter(db);
    await store.initialize();
    expect(await store.readCache()).toEqual(emptyCache());
  });
});

test("recent history is ordered by latest local reading time", async () => {
  const history = new HistoryStore(new MemoryDatabase());
  await history.record(content("1"), "2026-10-20T00:00:00Z");
  await history.record(content("2"), now);
  expect((await history.list()).map((row) => row.postId)).toEqual(["2", "1"]);
});
