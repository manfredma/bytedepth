import { LocalReaderRepository } from "../ReaderRepository";
import { MobileRequestError } from "../../api/errors";
import { CacheIndex } from "../../storage/CacheIndex";
import { HistoryStore } from "../../storage/HistoryStore";
import { MemoryDatabase, MemoryFiles, content, hash, now } from "../../storage/__tests__/fixtures";
import type { PublicContent, ReaderSnapshot, MobileApi, MobileApiRequestOptions } from "../../contracts/mobile";
function available(c: PublicContent) {
  const { postId, contentVersion, projectionVersion, digest, representationHash, shellVersion } = c;
  return {
    schemaVersion: 1,
    postId,
    availability: "AVAILABLE",
    identity: { postId, contentVersion, projectionVersion, digest, representationHash, shellVersion },
  };
}
function setup() {
  const db = new MemoryDatabase();
  const files = new MemoryFiles();
  const index = new CacheIndex(db, files, hash);
  let body = content();
  let state: unknown = available(body);
  const api: MobileApi = {
    async request<T>(path: string): Promise<T> {
      return (path.endsWith("/state") ? state : body) as T;
    },
  };
  const repository = new LocalReaderRepository({
    api,
    index,
    history: new HistoryStore(db),
    downloader: {
      async download() {
        throw new Error("image failed");
      },
    },
  });
  return {
    db,
    files,
    index,
    repository,
    api,
    setBody(c: PublicContent) {
      body = c;
      state = available(c);
    },
    setState(value: unknown) {
      state = value;
    },
  };
}
async function until(iterator: AsyncIterator<ReaderSnapshot>, predicate: (s: ReaderSnapshot) => boolean) {
  for (let i = 0; i < 12; i++) {
    const next = await iterator.next();
    if (next.done) throw new Error("stream closed");
    if (predicate(next.value)) return next.value;
  }
  throw new Error("No matching snapshot");
}
describe("reader lifecycle", () => {
  test("first open automatically saves public content and history", async () => {
    const { repository } = setup();
    const stream = repository.open("1")[Symbol.asyncIterator]();
    const saved = await until(stream, (s) => s.copyState === "OFFLINE_READY");
    expect(saved.content).toEqual(content());
    expect((await repository.listRecent())[0]).toMatchObject({
      postId: "1",
      title: "Title 1",
      copyState: "OFFLINE_READY",
    });
    repository.release("1");
  });
  test("old complete copy is immediate and newer representation waits for explicit refresh", async () => {
    const { repository, index, setBody } = setup();
    await index.promote(content(), [], 0, now);
    setBody(content("1", 2));
    const stream = repository.open("1")[Symbol.asyncIterator]();
    expect((await stream.next()).value.content.contentVersion).toBe(1);
    const update = await until(stream, (s) => s.checkState === "UPDATE_AVAILABLE");
    expect(update.content?.contentVersion).toBe(1);
    await repository.refresh("1");
    expect((await until(stream, (s) => s.content?.contentVersion === 2)).copyState).toBe("OFFLINE_READY");
    repository.release("1");
  });
  test("failed images keep online body readable without claiming offline ready", async () => {
    const { repository, setBody } = setup();
    setBody(content("1", 1, true));
    const stream = repository.open("1")[Symbol.asyncIterator]();
    const failed = await until(stream, (s) => s.checkState === "FAILED");
    expect(failed.copyState).toBe("ONLINE_ONLY");
    expect(failed.content?.title).toBe("Title 1");
    repository.release("1");
  });
  test("trusted unavailable state closes old body; malformed and gateway errors do not withdraw", async () => {
    const { repository, index, setState } = setup();
    await index.promote(content(), [], 0, now);
    setState({ schemaVersion: 1, postId: "1", availability: "UNAVAILABLE" });
    const stream = repository.open("1")[Symbol.asyncIterator]();
    const withdrawn = await until(stream, (s) => s.copyState === "UNAVAILABLE");
    expect(withdrawn.content).toBeNull();
    expect(await index.get("1")).toBeNull();
    repository.release("1");
  });
});

describe("failure and cancellation boundaries", () => {
  test("five second timeout keeps the old article readable and clears timer", async () => {
    jest.useFakeTimers();
    try {
      const { repository, index, api } = setup();
      await index.promote(content(), [], 0, now);
      api.request = async () => new Promise(() => undefined);
      const stream = repository.open("1")[Symbol.asyncIterator]();
      expect((await stream.next()).value.copyState).toBe("OFFLINE_READY");
      const failed = until(stream, (s) => s.checkState === "FAILED");
      await jest.advanceTimersByTimeAsync(5000);
      expect((await failed).content?.contentVersion).toBe(1);
      expect(jest.getTimerCount()).toBe(0);
      repository.release("1");
    } finally {
      jest.useRealTimers();
    }
  });
  test.each(["gateway", "malformed", "wrong-id"])("%s state does not delete a known complete copy", async (failure) => {
    const { repository, index, api, setState } = setup();
    await index.promote(content(), [], 0, now);
    if (failure === "gateway")
      api.request = async () => {
        throw new Error("404");
      };
    else if (failure === "malformed") setState({ availability: "UNAVAILABLE" });
    else setState({ schemaVersion: 1, postId: "2", availability: "UNAVAILABLE" });
    const stream = repository.open("1")[Symbol.asyncIterator]();
    const snapshot = await until(stream, (s) => s.checkState === "FAILED");
    expect(snapshot.copyState).toBe("OFFLINE_READY");
    expect(await index.get("1")).not.toBeNull();
    repository.release("1");
  });
  test("failed refresh retains complete body and precise local position", async () => {
    const { repository, index, setBody, files } = setup();
    await index.promote(content(), [], 0, now);
    setBody(content("1", 2));
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await until(stream, (s) => s.checkState === "UPDATE_AVAILABLE");
    const position = {
      postId: "1",
      contentVersion: 1,
      projectionVersion: 1,
      segmentId: "p1",
      codePointOffset: 2,
      blockKind: "paragraph",
      blockText: "Hello",
      precedingHeadings: [],
      viewportRatio: 0.2,
      progress: 0.3,
      updatedAt: now,
    };
    await repository.savePosition(position);
    files.failWrite = true;
    await repository.refresh("1");
    expect((await until(stream, (s) => s.checkState === "FAILED")).content?.contentVersion).toBe(1);
    files.failWrite = false;
    await index.invalidate("1");
    expect((await repository.listRecent())[0]).toMatchObject({ position, copyState: "UNAVAILABLE" });
    repository.release("1");
  });
  test("explicit refresh retries an online body whose initial image failed", async () => {
    const { repository, index, setBody } = setup();
    const c = content("1", 1, true);
    setBody(c);
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await until(stream, (s) => s.checkState === "FAILED");
    const bytes = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
    repository.dependencies.downloader.download = async () => ({
      key: "image",
      bytes,
      mediaType: "image/png",
      sha256: await hash(bytes),
    });
    await repository.refresh("1");
    expect((await index.get("1"))?.content.postId).toBe("1");
    repository.release("1");
  });
  test("withdrawal on reopened article wins a late image response", async () => {
    const { repository, index, setBody, setState } = setup();
    setBody(content("1", 1, true));
    let finish!: (value: { key: string; bytes: Uint8Array; mediaType: string; sha256: string }) => void;
    const downloading = new Promise<{ key: string; bytes: Uint8Array; mediaType: string; sha256: string }>(
      (resolve) => {
        finish = resolve;
      },
    );
    repository.dependencies.downloader.download = async () => downloading;
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await until(stream, (s) => s.copyState === "ONLINE_ONLY");
    repository.release("1");
    setState({ schemaVersion: 1, postId: "1", availability: "UNAVAILABLE" });
    const reopened = repository.open("1")[Symbol.asyncIterator]();
    await until(reopened, (s) => s.copyState === "UNAVAILABLE");
    const bytes = new Uint8Array([1]);
    finish({ key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) });
    await Promise.resolve();
    await Promise.resolve();
    expect(await index.get("1")).toBeNull();
    expect(await index.unavailable("1")).toBe(true);
    repository.release("1");
  });
});

describe("stream trust and bounded work", () => {
  test("release discards queued content and completes the stream", async () => {
    const { repository } = setup();
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await stream.next();
    await repository.refresh("1");
    repository.release("1");
    expect((await stream.next()).done).toBe(true);
  });
  test("same representation confirmation persists the check timestamp", async () => {
    const { repository, index } = setup();
    await index.promote(content(), [], 0, now);
    const stream = repository.open("1")[Symbol.asyncIterator]();
    const current = await until(stream, (s) => s.checkState === "CURRENT");
    expect(current.copyState).toBe("OFFLINE_READY");
    expect((await index.get("1"))?.lastConfirmedAt).toBe(current.lastConfirmedAt);
    repository.release("1");
  });
  test("the device history remains after file eviction and fresh repository creation", async () => {
    const { repository, db, index, files, api } = setup();
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await until(stream, (s) => s.copyState === "OFFLINE_READY");
    repository.release("1");
    files.values.clear();
    const another = new LocalReaderRepository({
      api,
      index,
      history: new HistoryStore(db),
      downloader: {
        async download() {
          throw new Error("offline");
        },
      },
    });
    expect((await another.listRecent())[0]).toMatchObject({ title: "Title 1", copyState: "NONE" });
  });
  test("malformed content and state/content version mismatch never reach disk or history", async () => {
    const { repository, api, index } = setup();
    api.request = async <T>(path: string) => (path.endsWith("/state") ? available(content()) : content("2")) as T;
    const stream = repository.open("1")[Symbol.asyncIterator]();
    expect((await until(stream, (s) => s.checkState === "FAILED")).content).toBeNull();
    expect(await index.get("1")).toBeNull();
    expect(await repository.listRecent()).toEqual([]);
    repository.release("1");
  });
  test("single flight shares one content retrieval across two readers and refresh", async () => {
    const { repository, api } = setup();
    let count = 0;
    let resolve!: (value: unknown) => void;
    const state = new Promise<unknown>((r) => {
      resolve = r;
    });
    api.request = async <T>(path: string) => {
      if (path.endsWith("/state")) return (await state) as T;
      count++;
      return content() as T;
    };
    const a = repository.open("1")[Symbol.asyncIterator](),
      b = repository.open("1")[Symbol.asyncIterator]();
    await a.next();
    await b.next();
    const refresh = repository.refresh("1");
    resolve(available(content()));
    await refresh;
    expect((await until(a, (s) => s.copyState === "OFFLINE_READY")).content?.postId).toBe("1");
    expect((await until(b, (s) => s.copyState === "OFFLINE_READY")).content?.postId).toBe("1");
    expect(count).toBe(1);
    repository.release("1");
    await a.next();
    await b.next();
  });
  test("failed capacity reservation keeps online body without a false complete marker", async () => {
    const { repository, index } = setup();
    Object.defineProperty(index, "budget", { value: 0 });
    const stream = repository.open("1")[Symbol.asyncIterator]();
    const failed = await until(stream, (s) => s.checkState === "FAILED");
    expect(failed.copyState).toBe("ONLINE_ONLY");
    expect(await index.get("1")).toBeNull();
    repository.release("1");
  });
  test("nondecimal article IDs are rejected before network access", async () => {
    const { repository } = setup();
    await expect(repository.open("1e3")[Symbol.asyncIterator]().next()).rejects.toThrow("Invalid article");
    repository.release("absent");
  });
});

test("an old iterator closing cannot release a reopened session for the same article", async () => {
  const { repository } = setup();
  const old = repository.open("1")[Symbol.asyncIterator]();
  await old.next();
  await repository.refresh("1");
  repository.release("1");
  const reopened = repository.open("1")[Symbol.asyncIterator]();
  await reopened.next();
  expect((await old.next()).done).toBe(true);
  await repository.refresh("1");
  const current = await until(reopened, (s) => s.checkState === "CURRENT");
  expect(current.content?.postId).toBe("1");
  repository.release("1");
});

test("release cancels a hanging state check without leaving the five-second timer", async () => {
  jest.useFakeTimers();
  try {
    const { repository, api } = setup();
    api.request = async () => new Promise(() => undefined);
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await stream.next();
    repository.release("1");
    await stream.next();
    await Promise.resolve();
    await Promise.resolve();
    expect(jest.getTimerCount()).toBe(0);
  } finally {
    jest.useRealTimers();
  }
});

describe("cancellation at asynchronous storage boundaries", () => {
  function gate() {
    let finish!: () => void, started!: () => void;
    const wait = new Promise<void>((r) => {
        finish = r;
      }),
      start = new Promise<void>((r) => {
        started = r;
      });
    return { wait, start, finish, started };
  }
  test.each(["allow", "content", "history", "download", "promote", "confirmed", "final-history"])(
    "release at %s cannot publish late content",
    async (boundary) => {
      const { repository, index, api, setBody } = setup();
      const barrier = gate();
      if (boundary === "confirmed") await index.promote(content(), [], 0, now);
      if (boundary === "download") setBody(content("1", 1, true));
      if (boundary === "allow") {
        const original = index.allow.bind(index);
        index.allow = async (id) => {
          barrier.started();
          await barrier.wait;
          await original(id);
        };
      }
      if (boundary === "content") {
        const original = api.request.bind(api);
        api.request = async <T>(path: string, options: MobileApiRequestOptions) => {
          if (path.endsWith("/content")) {
            barrier.started();
            await barrier.wait;
          }
          return original<T>(path, options);
        };
      }
      if (boundary === "history" || boundary === "final-history") {
        const original = repository.dependencies.history.record.bind(repository.dependencies.history);
        let calls = 0;
        repository.dependencies.history.record = async (c, t) => {
          calls++;
          if (calls === (boundary === "history" ? 1 : 2)) {
            barrier.started();
            await barrier.wait;
          }
          await original(c, t);
        };
      }
      if (boundary === "download") {
        repository.dependencies.downloader.download = async () => {
          barrier.started();
          await barrier.wait;
          const bytes = new Uint8Array([1]);
          return { key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) };
        };
      }
      if (boundary === "promote") {
        const original = index.promote.bind(index);
        index.promote = async (...args) => {
          barrier.started();
          await barrier.wait;
          return original(...args);
        };
      }
      if (boundary === "confirmed") {
        const original = index.confirm.bind(index);
        index.confirm = async (...args) => {
          barrier.started();
          await barrier.wait;
          return original(...args);
        };
      }
      const stream = repository.open("1")[Symbol.asyncIterator]();
      await stream.next();
      await barrier.start;
      repository.release("1");
      barrier.finish();
      expect((await stream.next()).done).toBe(true);
      // Drain late continuations through the real index serial boundary.
      await Promise.resolve();
      await index.get("1");
      expect((await stream.next()).done).toBe(true);
    },
  );
  test("release before cached hydration returns prevents the first snapshot", async () => {
    const { repository, index } = setup();
    const barrier = gate();
    const get = index.get.bind(index);
    index.get = async (id) => {
      barrier.started();
      await barrier.wait;
      return get(id);
    };
    const stream = repository.open("1")[Symbol.asyncIterator]();
    const first = stream.next();
    await barrier.start;
    repository.release("1");
    barrier.finish();
    expect((await first).done).toBe(true);
  });
  test("refresh cancelled during initial hydration never starts a network check", async () => {
    const { repository, index } = setup();
    const barrier = gate();
    const get = index.get.bind(index);
    index.get = async (id) => {
      barrier.started();
      await barrier.wait;
      return get(id);
    };
    const refreshing = repository.refresh("1");
    await barrier.start;
    repository.release("1");
    barrier.finish();
    await refreshing;
    expect(await repository.listRecent()).toEqual([]);
  });
});

describe("actual image decode integrity", () => {
  test("a decoder-rejected image never becomes an offline complete package", async () => {
    const { repository, index, setBody } = setup();
    setBody(content("1", 1, true));
    const bytes = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
    repository.dependencies.downloader.download = async () => ({
      key: "image",
      bytes,
      mediaType: "image/png",
      sha256: await hash(bytes),
    });
    repository.dependencies.verifyAsset = async () => {
      throw new Error("decoder rejected truncated PNG");
    };
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await stream.next();
    await repository.refresh("1");
    expect(await index.get("1")).toBeNull();
    expect((await until(stream, (s) => s.checkState === "FAILED")).copyState).toBe("ONLINE_ONLY");
    repository.release("1");
  });
  test("WebView image failure atomically downgrades matching complete body while keeping local history", async () => {
    const { repository, index } = setup();
    const c = content("1", 1, true),
      bytes = new Uint8Array([1]);
    await index.promote(c, [{ key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) }], 0, now);
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await stream.next();
    await repository.reportImageFailure("1", "image", c.representationHash);
    expect(await index.get("1")).toBeNull();
    const failed = await until(stream, (s) => s.checkState === "FAILED");
    expect(failed.copyState).toBe("ONLINE_ONLY");
    expect(failed.content).toEqual(c);
    expect((await repository.listRecent())[0].title).toBe(c.title);
    repository.release("1");
  });
  test("a stale renderer failure cannot downgrade a newer representation", async () => {
    const { repository, index } = setup();
    await index.promote(content("1", 2), [], 0, now);
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await stream.next();
    await repository.reportImageFailure("1", "unlisted", "r1");
    expect((await index.get("1"))?.content.contentVersion).toBe(2);
    repository.release("1");
  });
});

test("native decoder failure during refresh preserves old complete copy and position", async () => {
  const { repository, index, setBody } = setup();
  await index.promote(content(), [], 0, now);
  setBody(content("1", 2, true));
  const bytes = new Uint8Array([1]);
  repository.dependencies.downloader.download = async () => ({
    key: "image",
    bytes,
    mediaType: "image/png",
    sha256: await hash(bytes),
  });
  repository.dependencies.verifyAsset = async () => {
    throw new Error("invalid encoded image");
  };
  const stream = repository.open("1")[Symbol.asyncIterator]();
  await until(stream, (s) => s.checkState === "UPDATE_AVAILABLE");
  await repository.refresh("1");
  expect((await until(stream, (s) => s.checkState === "FAILED")).content?.contentVersion).toBe(1);
  expect((await index.get("1"))?.content.contentVersion).toBe(1);
  expect((await repository.listRecent())[0].title).toBe("Title 1");
  repository.release("1");
});

test.each(["body", "asset"])("oversized %s cannot bypass the per-article memory/package cap", async (source) => {
  const { repository, index, setBody } = setup();
  const c = content("1", 1, source === "asset");
  if (source === "body") c.html = "x".repeat(33554432);
  setBody(c);
  repository.dependencies.downloader.download = async () => ({
    key: "image",
    bytes: new Uint8Array(33554432),
    mediaType: "image/png",
    sha256: "0".repeat(64),
  });
  const stream = repository.open("1")[Symbol.asyncIterator]();
  const failed = await until(stream, (s) => s.checkState === "FAILED");
  expect(failed.copyState).toBe("ONLINE_ONLY");
  expect(await index.get("1")).toBeNull();
  repository.release("1");
});

test("image failure accepts an unlisted manifest image but ignores unrelated keys and absent sessions", async () => {
  const { repository, index } = setup();
  await repository.reportImageFailure("1", "image", "r1");
  await index.promote(content(), [], 0, now);
  const stream = repository.open("1")[Symbol.asyncIterator]();
  await stream.next();
  await repository.reportImageFailure("1", "not-a-resource", "r1");
  expect(await index.get("1")).not.toBeNull();
  await repository.reportImageFailure("1", "unlisted", "r1");
  expect(await index.get("1")).toBeNull();
  repository.release("1");
});

test("renderer failure invalidates a pending decoder and permits a fresh explicit retry", async () => {
  const { repository, index, setBody } = setup();
  const c = content("1", 2, true);
  setBody(c);
  const bytes = new Uint8Array([1]);
  repository.dependencies.downloader.download = async () => ({
    key: "image",
    bytes,
    mediaType: "image/png",
    sha256: await hash(bytes),
  });
  await index.promote(
    content("1", 1, true),
    [{ key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) }],
    0,
    now,
  );
  let finish!: () => void, started!: () => void;
  const decoding = new Promise<void>((r) => {
      started = r;
    }),
    blocked = new Promise<void>((r) => {
      finish = r;
    });
  repository.dependencies.verifyAsset = async () => {
    started();
    await blocked;
  };
  const stream = repository.open("1")[Symbol.asyncIterator]();
  await until(stream, (value) => value.checkState === "UPDATE_AVAILABLE");
  const previousRefresh = repository.refresh("1");
  await decoding;
  await repository.reportImageFailure("1", "image", "r1");
  repository.dependencies.verifyAsset = async () => undefined;
  const retry = repository.refresh("1");
  await new Promise<void>((resolve) => setImmediate(resolve));
  const savedBeforeOldDecoderFinished = await index.get("1");
  finish();
  await retry;
  await previousRefresh;
  expect(savedBeforeOldDecoderFinished?.content.contentVersion).toBe(2);
  repository.release("1");
});

test("a known withdrawal remains unavailable when a later state check fails", async () => {
  const { repository, index, api } = setup();
  await index.invalidate("1");
  api.request = async () => {
    throw new Error("offline");
  };
  const stream = repository.open("1")[Symbol.asyncIterator]();
  expect((await stream.next()).value.copyState).toBe("UNAVAILABLE");
  expect((await until(stream, (s) => s.checkState === "FAILED")).content).toBeNull();
  repository.release("1");
});
test("returning the last consumer closes its session without deleting its complete copy", async () => {
  const { repository, index } = setup();
  const stream = repository.open("1")[Symbol.asyncIterator]();
  await until(stream, (s) => s.copyState === "OFFLINE_READY");
  await stream.return?.();
  expect((await stream.next()).done).toBe(true);
  expect(await index.get("1")).not.toBeNull();
});
test("consumer release at completed-check cleanup cannot fetch or publish new content", async () => {
  const { index, db, api } = setup();
  let repository: LocalReaderRepository;
  repository = new LocalReaderRepository({
    api,
    index,
    history: new HistoryStore(db),
    downloader: {
      async download() {
        throw new Error("not needed");
      },
    },
    clock: {
      now: () => now,
      setTimeout: (fn, ms) => setTimeout(fn, ms),
      clearTimeout(timer) {
        clearTimeout(timer);
        repository.release("1");
      },
    },
  });
  const stream = repository.open("1")[Symbol.asyncIterator]();
  await stream.next();
  expect((await stream.next()).done).toBe(true);
  expect(await index.get("1")).toBeNull();
  expect(await repository.listRecent()).toEqual([]);
});

test("expected missing-image placeholders during initial saving cannot cancel automatic cache", async () => {
  const { repository, index, setBody } = setup();
  setBody(content("1", 1, true));
  let finish!: () => void, started!: () => void;
  const startedDownload = new Promise<void>((r) => {
      started = r;
    }),
    blocked = new Promise<void>((r) => {
      finish = r;
    });
  const bytes = new Uint8Array([1]);
  repository.dependencies.downloader.download = async () => {
    started();
    await blocked;
    return { key: "image", bytes, mediaType: "image/png", sha256: await hash(bytes) };
  };
  const stream = repository.open("1")[Symbol.asyncIterator]();
  await stream.next();
  await startedDownload;
  await repository.reportImageFailure("1", "image", "r1");
  finish();
  await new Promise<void>((resolve) => setImmediate(resolve));
  expect((await index.get("1"))?.content.postId).toBe("1");
  repository.release("1");
});

test("postcommit cleanup failure reconciles displayed identity with the valid durable new copy", async () => {
  const { repository, index, files, setBody } = setup();
  await index.promote(content(), [], 0, now);
  setBody(content("1", 2));
  const stream = repository.open("1")[Symbol.asyncIterator]();
  await until(stream, (s) => s.checkState === "UPDATE_AVAILABLE");
  files.failRemove = true;
  await repository.refresh("1");
  files.failRemove = false;
  const failed = await until(stream, (s) => s.checkState === "FAILED");
  expect(failed.copyState).toBe("OFFLINE_READY");
  expect(failed.content?.contentVersion).toBe(2);
  await index.recover();
  repository.release("1");
});

test("a full public JSON larger than 2MiB remains online only even below the package cap", async () => {
  const { repository, index, setBody } = setup();
  const c = content();
  c.html = "x".repeat(2097152);
  setBody(c);
  const stream = repository.open("1")[Symbol.asyncIterator]();
  const failed = await until(stream, (s) => s.checkState === "FAILED");
  expect(failed.copyState).toBe("ONLINE_ONLY");
  expect(failed.content?.title).toBe("Title 1");
  expect(await index.get("1")).toBeNull();
  repository.release("1");
});

describe("committed-copy reconciliation failures", () => {
  test.each(["missing", "unreadable", "release", "release-unreadable"])(
    "%s after commit never leaves a false complete marker or revives a closed page",
    async (fault) => {
      const { repository, index, files, setBody } = setup();
      await index.promote(content(), [], 0, now);
      setBody(content("1", 2));
      const stream = repository.open("1")[Symbol.asyncIterator]();
      await until(stream, (s) => s.checkState === "UPDATE_AVAILABLE");
      const get = index.get.bind(index);
      index.get = async (id) => {
        if (fault.startsWith("release")) repository.release("1");
        if (fault.includes("unreadable")) throw new Error("storage could not confirm");
        if (fault === "missing") files.values.clear();
        return get(id);
      };
      files.failRemove = true;
      await repository.refresh("1");
      files.failRemove = false;
      if (fault.startsWith("release")) expect((await stream.next()).done).toBe(true);
      else {
        const failed = await until(stream, (s) => s.checkState === "FAILED");
        expect(failed.copyState).toBe("ONLINE_ONLY");
        expect(failed.content?.contentVersion).toBe(1);
        repository.release("1");
      }
    },
  );
});

test("withdrawal cleanup failure is observable while the withdrawn body remains closed", async () => {
  const { repository, index, files, setState } = setup();
  await index.promote(content(), [], 0, now);
  files.failRemove = true;
  setState({ schemaVersion: 1, postId: "1", availability: "UNAVAILABLE" });
  const stream = repository.open("1")[Symbol.asyncIterator]();
  const failed = await until(stream, (s) => s.checkState === "FAILED");
  expect(failed.copyState).toBe("UNAVAILABLE");
  expect(failed.content).toBeNull();
  files.failRemove = false;
  await index.recover();
  repository.release("1");
});

test("withdrawal cleanup failure after page release cannot revive the closed stream", async () => {
  const { repository, index, files, setState } = setup();
  await index.promote(content(), [], 0, now);
  setState({ schemaVersion: 1, postId: "1", availability: "UNAVAILABLE" });
  let finish!: () => void, started!: () => void;
  const deleting = new Promise<void>((r) => {
      started = r;
    }),
    blocked = new Promise<void>((r) => {
      finish = r;
    });
  files.remove = async () => {
    started();
    await blocked;
    throw new Error("storage removal failed");
  };
  const stream = repository.open("1")[Symbol.asyncIterator]();
  await stream.next();
  await deleting;
  repository.release("1");
  finish();
  expect((await stream.next()).done).toBe(true);
});

describe("Task 6 review regression", () => {
  test("a history failure after promotion cannot leave the removed old version marked complete", async () => {
    const { repository, index, files, setBody } = setup();
    await index.promote(content(), [], 0, now);
    const oldBody = (await index.db.readCache()).copies["1"].body;
    setBody(content("1", 2));
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await until(stream, (snapshot) => snapshot.checkState === "UPDATE_AVAILABLE");
    const position = {
      postId: "1",
      contentVersion: 1,
      projectionVersion: 1,
      segmentId: "p1",
      codePointOffset: 2,
      blockKind: "paragraph",
      blockText: "Hello",
      precedingHeadings: [],
      viewportRatio: 0.2,
      progress: 0.3,
      updatedAt: now,
    };
    await repository.savePosition(position);
    const record = repository.dependencies.history.record.bind(repository.dependencies.history);
    repository.dependencies.history.record = async (publicContent, timestamp) => {
      if (publicContent.contentVersion === 2) throw new Error("history disk full after index commit");
      await record(publicContent, timestamp);
    };
    await repository.refresh("1");
    const failed = await until(stream, (snapshot) => snapshot.checkState === "FAILED");
    const durable = await index.get("1");
    expect(files.values.has(oldBody)).toBe(false);
    expect(durable?.content.contentVersion).toBe(2);
    expect(failed.content?.contentVersion).toBe(durable?.content.contentVersion);
    expect(failed.copyState).toBe("OFFLINE_READY");
    expect((await repository.listRecent())[0].position).toEqual(position);
    repository.release("1");
  });

  test("AVAILABLE state followed by structured content POST_UNAVAILABLE withdraws the old copy", async () => {
    const { repository, index, api, setBody } = setup();
    await index.promote(content(), [], 0, now);
    setBody(content("1", 2));
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await until(stream, (snapshot) => snapshot.checkState === "UPDATE_AVAILABLE");
    const request = api.request.bind(api);
    api.request = async <T>(path: string, options: MobileApiRequestOptions): Promise<T> => {
      if (path.endsWith("/content")) throw new MobileRequestError(404, "POST_UNAVAILABLE", "withdrawn after state");
      return request<T>(path, options);
    };
    await repository.refresh("1");
    const terminal = await until(
      stream,
      (snapshot) => snapshot.checkState === "FAILED" || snapshot.copyState === "UNAVAILABLE",
    );
    expect(terminal.copyState).toBe("UNAVAILABLE");
    expect(terminal.content).toBeNull();
    expect(await index.get("1")).toBeNull();
    expect(await index.unavailable("1")).toBe(true);
    expect(await index.generation("1")).toBe(1);
    expect((await repository.listRecent())[0]).toMatchObject({ title: "Title 1", copyState: "UNAVAILABLE" });
    repository.release("1");
  });
});

describe("Task 6 review withdrawal trust boundary", () => {
  test.each([
    ["ordinary structured 404", new MobileRequestError(404, "INTERNAL_ERROR", "gateway unavailable")],
    ["401 with unavailable-looking code", new MobileRequestError(401, "POST_UNAVAILABLE", "session expired")],
    ["503 with unavailable-looking code", new MobileRequestError(503, "POST_UNAVAILABLE", "gateway failure")],
    ["transport failure", new Error("network disconnected")],
    ["unvalidated error-shaped object", { status: 404, code: "POST_UNAVAILABLE" }],
  ])("%s cannot withdraw an existing copy", async (_label, failure) => {
    const { repository, index, api, setBody } = setup();
    await index.promote(content(), [], 0, now);
    setBody(content("1", 2));
    const stream = repository.open("1")[Symbol.asyncIterator]();
    await until(stream, (snapshot) => snapshot.checkState === "UPDATE_AVAILABLE");
    const request = api.request.bind(api);
    api.request = async <T>(path: string, options: MobileApiRequestOptions): Promise<T> => {
      if (path.endsWith("/content")) throw failure;
      return request<T>(path, options);
    };
    await repository.refresh("1");
    const failed = await until(stream, (snapshot) => snapshot.checkState === "FAILED");
    expect(failed.copyState).toBe("OFFLINE_READY");
    expect(failed.content?.contentVersion).toBe(1);
    expect((await index.get("1"))?.content.contentVersion).toBe(1);
    expect(await index.unavailable("1")).toBe(false);
    expect(await index.generation("1")).toBe(0);
    repository.release("1");
  });

  test("a late structured content withdrawal cannot invalidate a reopened newer session", async () => {
    const { repository, index, api, setBody } = setup();
    await index.promote(content(), [], 0, now);
    setBody(content("1", 2));
    const oldStream = repository.open("1")[Symbol.asyncIterator]();
    await until(oldStream, (snapshot) => snapshot.checkState === "UPDATE_AVAILABLE");
    let rejectOld!: (reason: unknown) => void;
    let started!: () => void;
    const pending = new Promise<never>((_resolve, reject) => {
      rejectOld = reject;
    });
    const fetching = new Promise<void>((resolve) => {
      started = resolve;
    });
    const request = api.request.bind(api);
    let oldRequestPending = true;
    api.request = async <T>(path: string, options: MobileApiRequestOptions): Promise<T> => {
      if (path.endsWith("/content") && oldRequestPending) {
        oldRequestPending = false;
        started();
        return pending;
      }
      return request<T>(path, options);
    };
    const oldRefresh = repository.refresh("1");
    await fetching;
    repository.release("1");
    const reopened = repository.open("1")[Symbol.asyncIterator]();
    await until(reopened, (snapshot) => snapshot.checkState === "UPDATE_AVAILABLE");
    await repository.refresh("1");
    await until(
      reopened,
      (snapshot) => snapshot.copyState === "OFFLINE_READY" && snapshot.content?.contentVersion === 2,
    );
    rejectOld(new MobileRequestError(404, "POST_UNAVAILABLE", "old response"));
    await oldRefresh;
    expect((await index.get("1"))?.content.contentVersion).toBe(2);
    expect(await index.unavailable("1")).toBe(false);
    expect(await index.generation("1")).toBe(0);
    expect((await oldStream.next()).done).toBe(true);
    repository.release("1");
  });
});
