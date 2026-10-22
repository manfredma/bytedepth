import { CacheIndex } from "../CacheIndex";
import { MemoryDatabase, MemoryFiles, content, hash, now } from "./fixtures";
describe("atomic offline index", () => {
  test("publishes a complete body after its file is durable", async () => {
    const db = new MemoryDatabase();
    const files = new MemoryFiles();
    const index = new CacheIndex(db, files, hash);
    expect(await index.promote(content(), [], 0, now)).toBe(true);
    expect((await index.get("1"))?.content).toEqual(content());
  });
  test("cannot mark body with a missing required image complete", async () => {
    const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash);
    await expect(index.promote(content("1", 1, true), [], 0, now)).rejects.toThrow();
    expect(await index.get("1")).toBeNull();
  });
  test.each(["write", "move", "commit"])("a failed %s preserves the previous complete article", async (boundary) => {
    const db = new MemoryDatabase();
    const files = new MemoryFiles();
    const index = new CacheIndex(db, files, hash);
    await index.promote(content(), [], 0, now);
    files.failWrite = boundary === "write";
    files.failMove = boundary === "move";
    db.failCommit = boundary === "commit";
    await expect(index.promote(content("1", 2), [], 0, now)).rejects.toThrow();
    db.failCommit = false;
    files.failWrite = false;
    files.failMove = false;
    expect((await index.get("1"))?.content.contentVersion).toBe(1);
  });
  test("does not sacrifice the current article to fit old and new bytes", async () => {
    const db = new MemoryDatabase();
    const files = new MemoryFiles();
    const index = new CacheIndex(db, files, hash, 700);
    await index.promote(content(), [], 0, now);
    index.setActive("1", true);
    expect(await index.promote(content("1", 2), [], 0, now)).toBe(false);
    expect((await index.get("1"))?.content.contentVersion).toBe(1);
    expect((await files.list()).reduce((n, f) => n + f.size, 0)).toBeLessThanOrEqual(700);
  });
});

describe("recovery, reference ownership and bounded bytes", () => {
  async function image() {
    const bytes = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
    return { key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) };
  }
  test("shared assets survive one withdrawal and disappear after the last reference", async () => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash);
    const asset = await image();
    await index.promote(content("1", 1, true), [asset], 0, now);
    await index.promote(content("2", 1, true), [asset], 0, now);
    expect((await files.list()).filter((f) => f.name.startsWith("a-")).length).toBe(1);
    await index.invalidate("1");
    expect((await index.get("2"))?.content.postId).toBe("2");
    expect(await index.assetDataUris(content("2", 1, true))).toEqual({ image: "data:image/png;base64,iVBORw0KGgo=" });
    await index.invalidate("2");
    expect(await files.list()).toEqual([]);
    expect(await index.promote(content(), [], 0, now)).toBe(false);
  });
  test("recovery removes precommit orphans while retaining committed complete content", async () => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash);
    await index.promote(content(), [], 0, now);
    await files.write("stage-900", new Uint8Array([1]));
    await files.write(`a-${"f".repeat(64)}`, new Uint8Array([2]));
    await new CacheIndex(db, files, hash).recover();
    expect((await index.get("1"))?.content).toEqual(content());
    expect((await files.list()).length).toBe(1);
  });
  test("postcommit cleanup failure keeps new copy and startup finishes cleanup", async () => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash);
    await index.promote(content(), [], 0, now);
    files.failRemove = true;
    await expect(index.promote(content("1", 2), [], 0, now)).rejects.toThrow();
    files.failRemove = false;
    await index.recover();
    expect((await index.get("1"))?.content.contentVersion).toBe(2);
    expect((await files.list()).length).toBe(1);
  });
  test.each(["missing", "corrupt"])("a %s image downgrades a complete copy", async (reason) => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash);
    await index.promote(content("1", 1, true), [await image()], 0, now);
    const asset = (await files.list()).find((f) => f.name.startsWith("a-"))!;
    if (reason === "missing") await files.remove(asset.name);
    else await files.write(asset.name, new Uint8Array([1]));
    expect(await index.get("1")).toBeNull();
    expect(await files.list()).toEqual([]);
  });
  test("staging and old complete bytes both count toward the budget", async () => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash, 700);
    await index.promote(content(), [], 0, now);
    index.setActive("1", true);
    await files.write("stage-700", new Uint8Array(250));
    expect(await index.promote(content("2"), [], 0, now)).toBe(false);
  });
  test("evicts least recently read unprotected copy and retains active copy", async () => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash, 1300);
    await index.promote(content("1"), [], 0, now);
    await index.promote(content("2"), [], 0, now);
    index.setActive("1", true);
    const c = content("3");
    c.html = "x".repeat(200);
    await index.promote(c, [], 0, now);
    expect(await index.get("2")).toBeNull();
    expect(await index.get("1")).not.toBeNull();
    expect(await index.get("3")).not.toBeNull();
  });
  test("rejects wrong resource digest, count, and unsafe disk names", async () => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash);
    const asset = await image();
    await expect(
      index.promote(content("1", 1, true), [{ ...asset, sha256: "0".repeat(64) }], 0, now),
    ).rejects.toThrow();
    await index.promote(content(), [], 0, now);
    db.cache.copies["1"].body = "../private";
    expect(await index.get("1")).toBeNull();
  });
  test("data URIs cannot expose a different representation", async () => {
    const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash);
    expect(await index.assetDataUris(content())).toEqual({});
    await index.promote(content(), [], 0, now);
    expect(await index.assetDataUris(content("1", 2))).toEqual({});
  });
});

describe("transaction race barriers", () => {
  test("withdrawal after files are written but before commit invalidates publication", async () => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash);
    let unblock!: () => void;
    let started!: () => void;
    const blocked = new Promise<void>((r) => {
        unblock = r;
      }),
      moving = new Promise<void>((r) => {
        started = r;
      });
    const move = files.move.bind(files);
    files.move = async (from, to) => {
      started();
      await blocked;
      await move(from, to);
    };
    const saving = index.promote(content(), [], 0, now);
    await moving;
    const withdrawing = index.invalidate("1");
    expect(await index.generation("1")).toBe(1);
    unblock();
    expect(await saving).toBe(false);
    await withdrawing;
    expect(await index.get("1")).toBeNull();
    expect(await files.list()).toEqual([]);
  });
  test("unavailable articles reject even a caller with the current generation until public state allows them", async () => {
    const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash);
    await index.invalidate("1");
    expect(await index.promote(content(), [], 1, now)).toBe(false);
    await index.allow("1");
    expect(await index.promote(content(), [], 1, now)).toBe(true);
  });
  test("a staging checksum failure never replaces old complete bytes", async () => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash);
    await index.promote(content(), [], 0, now);
    const write = files.write.bind(files);
    files.write = async (name, bytes) => {
      await write(name, new Uint8Array([...bytes, 0]));
    };
    await expect(index.promote(content("1", 2), [], 0, now)).rejects.toThrow("Staging verification");
    expect((await index.get("1"))?.content.contentVersion).toBe(1);
  });
  test("incorrect indexed article or manifest keys cannot make a damaged copy complete", async () => {
    const db = new MemoryDatabase(),
      files = new MemoryFiles(),
      index = new CacheIndex(db, files, hash);
    await index.promote(content(), [], 0, now);
    await index.promote(content("2"), [], 0, now);
    db.cache.copies["1"].body = db.cache.copies["2"].body;
    expect(await index.get("1")).toBeNull();
    await index.promote(content(), [], 0, now);
    db.cache.copies["1"].assets = { bogus: { file: "missing", mediaType: "image/png" } };
    expect(await index.get("1")).toBeNull();
    const bytes = new Uint8Array([1]),
      asset = { key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) };
    await index.promote(content("3", 1, true), [asset], 0, now);
    db.cache.copies["3"].assets = { wrong: db.cache.copies["3"].assets.image };
    await expect(index.assetDataUris(content("3", 1, true))).rejects.toThrow("Missing");
    expect(await index.get("3")).toBeNull();
  });
  test.each(["missing-key", "bad-digest-format", "expected-digest", "media"])(
    "rejects an invalid %s resource",
    async (kind) => {
      const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash),
        c = content("1", 1, true),
        bytes = new Uint8Array([1]);
      const asset = { key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) };
      if (kind === "missing-key") asset.key = "another";
      if (kind === "bad-digest-format") asset.sha256 = "invalid";
      if (kind === "expected-digest") c.resources[0].sha256 = "f".repeat(64);
      if (kind === "media") asset.mediaType = "text/html";
      await expect(index.promote(c, [asset], 0, now)).rejects.toThrow("Invalid resource");
    },
  );
  test("base64 encoding handles one-byte padding and bounded output chunks", async () => {
    const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash),
      c = content("1", 1, true);
    const bytes = new Uint8Array(12289);
    bytes[0] = 255;
    await index.promote(c, [{ key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) }], 0, now);
    const uri = (await index.assetDataUris(c)).image;
    expect(uri).toBe(`data:image/png;base64,${Buffer.from(bytes).toString("base64")}`);
  });
});

test("decoder downgrade cannot discard an already newer committed copy", async () => {
  const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash);
  await index.promote(content("1", 2), [], 0, now);
  expect(await index.discard("1", "r1")).toBe(false);
  expect((await index.get("1"))?.content.contentVersion).toBe(2);
});
test("failed staging and failed cleanup retain recoverable orphans without overwriting old content", async () => {
  const db = new MemoryDatabase(),
    files = new MemoryFiles(),
    index = new CacheIndex(db, files, hash);
  await index.promote(content(), [], 0, now);
  const write = files.write.bind(files);
  files.write = async (name, bytes) => write(name, new Uint8Array([...bytes, 0]));
  files.failRemove = true;
  await expect(index.promote(content("1", 2), [], 0, now)).rejects.toThrow("Staging verification");
  files.failRemove = false;
  await index.recover();
  expect((await index.get("1"))?.content.contentVersion).toBe(1);
  expect((await files.list()).length).toBe(1);
});

test("an impossible new package does not evict an existing readable article", async () => {
  const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash, 700);
  await index.promote(content(), [], 0, now);
  const oversized = content("2");
  oversized.html = "x".repeat(1000);
  expect(await index.promote(oversized, [], 0, now)).toBe(false);
  expect((await index.get("1"))?.content.postId).toBe("1");
});

test("LRU uses the last successful reading time among multiple eviction candidates", async () => {
  const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash, 1800);
  await index.promote(content("1"), [], 0, "2026-10-20T00:00:00Z");
  await index.promote(content("2"), [], 0, now);
  await index.promote(content("3"), [], 0, "2026-10-21T00:00:00Z");
  await index.touch("1", "2026-10-23T00:00:00Z");
  const c = content("4");
  c.html = "x".repeat(200);
  expect(await index.promote(c, [], 0, now)).toBe(true);
  expect(await index.get("3")).toBeNull();
  expect(await index.get("1")).not.toBeNull();
  expect(await index.get("2")).not.toBeNull();
  await index.touch("missing", now);
  await index.confirm("missing", now);
  expect(await index.get("missing")).toBeNull();
});

test("an evicted copy cannot reclaim a shared resource reserved by the incoming package", async () => {
  const db = new MemoryDatabase(),
    files = new MemoryFiles(),
    index = new CacheIndex(db, files, hash, 900),
    bytes = new Uint8Array([1]);
  const asset = { key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) };
  await index.promote(content("1", 1, true), [asset], 0, now);
  expect(await index.promote(content("2", 1, true), [asset], 0, now)).toBe(true);
  expect(await index.get("1")).toBeNull();
  expect((await index.get("2"))?.content.postId).toBe("2");
  expect((await files.list()).filter((file) => file.name.startsWith("a-")).length).toBe(1);
});

test("index cannot publish a full public JSON over 2MiB", async () => {
  const index = new CacheIndex(new MemoryDatabase(), new MemoryFiles(), hash);
  const c = content();
  c.html = "x".repeat(2097152);
  expect(await index.promote(c, [], 0, now)).toBe(false);
  expect(await index.get("1")).toBeNull();
});
