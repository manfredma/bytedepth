import { ARTICLE_BUDGET_BYTES, ARTICLE_BODY_BUDGET_BYTES, CHECK_TIMEOUT_MS } from "../config";
import {
  parsePostState,
  parsePublicContent,
  type MobileApi,
  type PostId,
  type ReaderRepository,
  type ReaderSnapshot,
  type ResumePosition,
  type RecentEntry,
  type PublicContent,
} from "../contracts/mobile";
import type { AssetDownloader, DownloadedAsset } from "../api/AssetDownloader";
import { MobileRequestError } from "../api/errors";
import { CacheIndex, CommittedCopyCleanupError, imageDataUri } from "../storage/CacheIndex";
import { openLocalDatabase } from "../storage/database";
import { createNativeFileStore } from "../storage/FileStore";
import { HistoryStore } from "../storage/HistoryStore";
import { sameRepresentation } from "./cachePolicy";
export interface ReaderClock {
  now(): string;
  setTimeout(callback: () => void, ms: number): ReturnType<typeof setTimeout>;
  clearTimeout(timer: ReturnType<typeof setTimeout>): void;
}
export interface ReaderDependencies {
  api: MobileApi;
  downloader: Pick<AssetDownloader, "download">;
  index: CacheIndex;
  history: HistoryStore;
  clock?: ReaderClock;
  verifyAsset?: (asset: DownloadedAsset) => Promise<void>;
}
const systemClock: ReaderClock = {
  now: () => new Date().toISOString(),
  setTimeout: (fn, ms) => setTimeout(fn, ms),
  clearTimeout: (timer) => clearTimeout(timer),
};
class SnapshotQueue {
  private values: ReaderSnapshot[] = [];
  private waiter: (() => void) | null = null;
  private closed = false;
  push(value: ReaderSnapshot) {
    this.values = [value];
    this.waiter?.();
    this.waiter = null;
  }
  close() {
    this.closed = true;
    this.values = [];
    this.waiter?.();
    this.waiter = null;
  }
  async next(): Promise<IteratorResult<ReaderSnapshot>> {
    while (!this.values.length && !this.closed)
      await new Promise<void>((resolve) => {
        this.waiter = resolve;
      });
    const value = this.values.shift();
    return value ? { done: false, value } : { done: true, value: undefined };
  }
}
interface Session {
  postId: string;
  snapshot: ReaderSnapshot;
  listeners: Set<SnapshotQueue>;
  ready: Promise<void>;
  epoch: number;
  released: boolean;
  flight?: Promise<void>;
  controller?: AbortController;
}
export class LocalReaderRepository implements ReaderRepository {
  private readonly sessions = new Map<string, Session>();
  private readonly clock: ReaderClock;
  constructor(readonly dependencies: ReaderDependencies) {
    this.clock = dependencies.clock ?? systemClock;
  }
  private session(postId: string): Session {
    const existing = this.sessions.get(postId);
    if (existing) return existing;
    if (!/^[1-9][0-9]*$/.test(postId)) throw new Error("Invalid article ID");
    const session: Session = {
      postId,
      snapshot: { postId, copyState: "NONE", checkState: "IDLE", content: null, lastConfirmedAt: null },
      listeners: new Set(),
      ready: Promise.resolve(),
      epoch: 0,
      released: false,
    };
    this.sessions.set(postId, session);
    this.dependencies.index.setActive(postId, true);
    session.ready = (async () => {
      const old = await this.dependencies.index.get(postId);
      if (session.released) return;
      if (old) {
        session.snapshot = {
          ...session.snapshot,
          copyState: "OFFLINE_READY",
          content: old.content,
          lastConfirmedAt: old.lastConfirmedAt,
        };
        await this.dependencies.index.touch(postId, this.clock.now());
        await this.dependencies.history.record(old.content, this.clock.now());
      } else if (await this.dependencies.index.unavailable(postId))
        session.snapshot = { ...session.snapshot, copyState: "UNAVAILABLE" };
    })();
    return session;
  }
  async *open(postId: PostId): AsyncIterable<ReaderSnapshot> {
    const session = this.session(postId);
    const queue = new SnapshotQueue();
    session.listeners.add(queue);
    try {
      await session.ready;
      if (session.released) return;
      queue.push({ ...session.snapshot, checkState: "CHECKING" });
      void this.run(session, false);
      for (;;) {
        const next = await queue.next();
        if (next.done) return;
        yield next.value;
      }
    } finally {
      session.listeners.delete(queue);
      if (!session.listeners.size && this.sessions.get(postId) === session) this.release(postId);
    }
  }
  private publish(session: Session, patch: Partial<ReaderSnapshot>): void {
    session.snapshot = { ...session.snapshot, ...patch };
    for (const queue of session.listeners) queue.push(session.snapshot);
  }
  async refresh(postId: PostId): Promise<void> {
    const session = this.session(postId);
    await session.ready;
    await this.run(session, true);
  }
  private async run(session: Session, refresh: boolean): Promise<void> {
    if (session.released) return;
    if (session.flight) return session.flight;
    const flight = this.perform(session, refresh);
    session.flight = flight;
    try {
      await flight;
    } finally {
      if (session.flight === flight) session.flight = undefined;
    }
  }
  private async perform(session: Session, refresh: boolean): Promise<void> {
    const epoch = session.epoch;
    const current = () => !session.released && session.epoch === epoch;
    const controller = new AbortController();
    session.controller = controller;
    this.publish(session, { checkState: "CHECKING" });
    let withdrawing = false;
    const withdraw = async () => {
      withdrawing = true;
      session.epoch++;
      controller.abort();
      this.publish(session, {
        copyState: "UNAVAILABLE",
        checkState: "CURRENT",
        content: null,
        lastConfirmedAt: this.clock.now(),
      });
      await this.dependencies.index.invalidate(session.postId);
    };
    try {
      const raw = await this.checkWithTimeout(session.postId, controller);
      if (!current()) return;
      const state = parsePostState(raw);
      if (state.postId !== session.postId) throw new Error("Article identity mismatch");
      if (state.availability === "UNAVAILABLE") {
        await withdraw();
        return;
      }
      await this.dependencies.index.allow(session.postId);
      if (!current()) return;
      const now = this.clock.now();
      const old = session.snapshot.content;
      if (old && session.snapshot.copyState === "OFFLINE_READY" && sameRepresentation(old, state.identity)) {
        await this.dependencies.index.confirm(session.postId, now);
        if (current()) this.publish(session, { checkState: "CURRENT", lastConfirmedAt: now });
        return;
      }
      if (old && !refresh) {
        this.publish(session, { checkState: "UPDATE_AVAILABLE", lastConfirmedAt: now });
        return;
      }
      const generation = await this.dependencies.index.generation(session.postId);
      let rawContent: unknown;
      try {
        rawContent = await this.dependencies.api.request<unknown>(`/posts/${session.postId}/content`, {
          method: "GET",
          signal: controller.signal,
        });
      } catch (error) {
        if (!current()) return;
        if (!(error instanceof MobileRequestError) || error.status !== 404 || error.code !== "POST_UNAVAILABLE")
          throw error;
        await withdraw();
        return;
      }
      const content = parsePublicContent(rawContent);
      if (!current()) return;
      if (!sameRepresentation(content, state.identity)) throw new Error("Content changed during check");
      // 新完整包提交前，继续显示现有完整内容。
      if (!old)
        this.publish(session, { content, copyState: "ONLINE_ONLY", checkState: "CURRENT", lastConfirmedAt: now });
      await this.dependencies.history.record(old ?? content, now);
      if (!current()) return;
      let size = new TextEncoder().encode(JSON.stringify(content)).length;
      if (size > ARTICLE_BODY_BUDGET_BYTES) throw new Error("Article body exceeds local package budget");
      const assets: DownloadedAsset[] = [];
      for (const resource of content.resources) {
        const asset = await this.dependencies.downloader.download(resource, controller.signal);
        if (!current()) return;
        size += asset.bytes.length;
        if (size > ARTICLE_BUDGET_BYTES) throw new Error("Article exceeds local package budget");
        await this.dependencies.verifyAsset?.(asset);
        if (!current()) return;
        assets.push(asset);
      }
      const saved = await this.dependencies.index.promote(content, assets, generation, now);
      if (!current()) return;
      if (!saved) throw new Error("No room for complete offline copy");
      // 可能失败的历史记录操作开始前，必须先公开已提交身份。
      this.publish(session, { content, copyState: "OFFLINE_READY", checkState: "CURRENT", lastConfirmedAt: now });
      await this.dependencies.history.record(content, now);
    } catch (error) {
      if (withdrawing) {
        if (!session.released) this.publish(session, { checkState: "FAILED" });
        return;
      }
      if (!current()) return;
      if (error instanceof CommittedCopyCleanupError) {
        // 发布已提交；报告清理失败前，先核对持久化身份。
        try {
          const saved = await this.dependencies.index.get(session.postId);
          if (!current()) return;
          if (saved) {
            this.publish(session, {
              content: saved.content,
              copyState: "OFFLINE_READY",
              lastConfirmedAt: saved.lastConfirmedAt,
              checkState: "FAILED",
            });
            return;
          }
        } catch {
          /* 存储无法确认已提交副本；当前可见正文仍仅在线可用。 */
        }
        if (current()) this.publish(session, { copyState: "ONLINE_ONLY", checkState: "FAILED" });
        return;
      }
      this.publish(session, { checkState: "FAILED" });
    }
  }
  private async checkWithTimeout(postId: string, controller: AbortController): Promise<unknown> {
    let rejectCheck!: (error: Error) => void;
    const cancelled = new Promise<never>((_, reject) => {
      rejectCheck = reject;
    });
    const onAbort = () => rejectCheck(new Error("State check cancelled"));
    controller.signal.addEventListener("abort", onAbort, { once: true });
    const timer = this.clock.setTimeout(() => {
      controller.abort();
      rejectCheck(new Error("State check timed out"));
    }, CHECK_TIMEOUT_MS);
    try {
      return await Promise.race([
        cancelled,
        this.dependencies.api.request<unknown>(`/posts/${postId}/state`, { method: "GET", signal: controller.signal }),
      ]);
    } finally {
      this.clock.clearTimeout(timer);
      controller.signal.removeEventListener("abort", onAbort);
    }
  }

  release(postId: PostId): void {
    const session = this.sessions.get(postId);
    if (!session) return;
    session.released = true;
    session.epoch++;
    session.controller?.abort();
    for (const listener of session.listeners) listener.close();
    this.dependencies.index.setActive(postId, false);
    this.sessions.delete(postId);
  }
  async savePosition(position: ResumePosition): Promise<void> {
    await this.dependencies.history.save(position);
  }
  async listRecent(): Promise<RecentEntry[]> {
    const result: RecentEntry[] = [];
    for (const entry of await this.dependencies.history.list())
      result.push({
        ...entry,
        copyState: (await this.dependencies.index.unavailable(entry.postId))
          ? "UNAVAILABLE"
          : (await this.dependencies.index.get(entry.postId))
            ? "OFFLINE_READY"
            : "NONE",
      });
    return result;
  }
  async reportImageFailure(postId: PostId, key: string, representationHash: string): Promise<void> {
    const session = this.sessions.get(postId);
    const content = session?.snapshot.content;
    if (
      !session ||
      !content ||
      session.snapshot.copyState !== "OFFLINE_READY" ||
      content.representationHash !== representationHash
    )
      return;
    if (key !== "unlisted" && !content.resources.some((resource) => resource.key === key)) return;
    session.epoch++;
    session.controller?.abort();
    session.flight = undefined;
    this.publish(session, { copyState: "ONLINE_ONLY", checkState: "FAILED" });
    await this.dependencies.index.discard(postId, representationHash);
  }
  async assetDataUris(content: PublicContent): Promise<Record<string, string>> {
    return this.dependencies.index.assetDataUris(content);
  }
}
export async function createNativeReaderRepository(
  api: MobileApi,
  downloader: Pick<AssetDownloader, "download">,
): Promise<LocalReaderRepository> {
  // eslint-disable-next-line @typescript-eslint/no-require-imports -- 注入仓库初始化时不加载原生凭据。
  const { sha256 }: typeof import("../auth/nativeCredentials") = require("../auth/nativeCredentials");
  // eslint-disable-next-line @typescript-eslint/no-require-imports -- 原生解码器仅由原生工厂使用。
  const { Image }: typeof import("react-native") = require("react-native");
  const index = new CacheIndex(await openLocalDatabase(), await createNativeFileStore(), sha256);
  await index.recover();
  return new LocalReaderRepository({
    api,
    downloader,
    index,
    history: new HistoryStore(index.db),
    verifyAsset: async (asset) => {
      // 被动 SVG 由 WebView 图片上下文解码；错误桥接会使该副本失效。
      if (asset.mediaType === "image/svg+xml") return;
      const size = await Image.getSize(imageDataUri(asset));
      if (!Number.isFinite(size.width) || !Number.isFinite(size.height) || size.width <= 0 || size.height <= 0)
        throw new Error("Image decoder returned invalid dimensions");
    },
  });
}
