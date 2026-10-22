import { CACHE_BUDGET_BYTES, ARTICLE_BODY_BUDGET_BYTES } from "../config";
import { parsePublicContent, type PublicContent } from "../contracts/mobile";
import type { DownloadedAsset } from "../api/AssetDownloader";
import type { CacheData, LocalDatabase, StoredCopy } from "./database";
import type { FileStore } from "./FileStore";

export class CommittedCopyCleanupError extends Error {
  constructor(cause: unknown) {
    super("Offline copy committed but cleanup failed", { cause });
  }
}

/** One owner serializes disk mutation and index publication; network I/O never holds this lock. */
export class CacheIndex {
  private tail: Promise<unknown> = Promise.resolve();
  private readonly active = new Set<string>();
  private sequence = 0;
  constructor(
    readonly db: LocalDatabase,
    readonly files: FileStore,
    readonly hash: (bytes: Uint8Array) => Promise<string>,
    readonly budget = CACHE_BUDGET_BYTES,
  ) {}
  private serial<T>(action: () => Promise<T>): Promise<T> {
    const result = this.tail.then(action);
    this.tail = result.catch(() => undefined);
    return result;
  }
  async generation(postId: string): Promise<number> {
    return (await this.db.readCache()).generations[postId] ?? 0;
  }
  setActive(postId: string, active: boolean): void {
    if (active) this.active.add(postId);
    else this.active.delete(postId);
  }
  async unavailable(postId: string): Promise<boolean> {
    return (await this.db.readCache()).unavailable[postId] === true;
  }
  async allow(postId: string): Promise<void> {
    await this.db.changeCache((data) => {
      delete data.unavailable[postId];
    });
  }
  async touch(postId: string, now: string): Promise<void> {
    await this.db.changeCache((data) => {
      if (data.copies[postId]) data.copies[postId].lastRead = now;
    });
  }
  async confirm(postId: string, now: string): Promise<void> {
    await this.db.changeCache((data) => {
      if (data.copies[postId]) data.copies[postId].lastConfirmedAt = now;
    });
  }
  async get(postId: string): Promise<{ content: PublicContent; lastConfirmedAt: string | null } | null> {
    return this.serial(async () => {
      const copy = (await this.db.readCache()).copies[postId];
      if (!copy) return null;
      try {
        const body = await this.verified(copy.body);
        const content = parsePublicContent(JSON.parse(new TextDecoder().decode(body)));
        if (content.postId !== postId || content.resources.length !== Object.keys(copy.assets).length)
          throw new Error("Invalid copy identity");
        for (const resource of content.resources) {
          const asset = copy.assets[resource.key];
          if (!asset) throw new Error("Missing resource");
          await this.verified(asset.file);
        }
        return { content, lastConfirmedAt: copy.lastConfirmedAt };
      } catch {
        await this.db.changeCache((data) => {
          delete data.copies[postId];
        });
        await this.collect();
        return null;
      }
    });
  }
  private async verified(name: string): Promise<Uint8Array> {
    if (!/^[ab]-[a-f0-9]{64}$/.test(name)) throw new Error("Invalid cache filename");
    const bytes = await this.files.read(name);
    if ((await this.hash(bytes)) !== name.slice(2)) throw new Error("Corrupt cache file");
    return bytes;
  }
  async assetDataUris(content: PublicContent): Promise<Record<string, string>> {
    return this.serial(async () => {
      const copy = (await this.db.readCache()).copies[content.postId];
      if (!copy) return {};
      const saved = parsePublicContent(JSON.parse(new TextDecoder().decode(await this.verified(copy.body))));
      if (saved.representationHash !== content.representationHash) return {};
      const result: Record<string, string> = {};
      for (const resource of content.resources) {
        const asset = copy.assets[resource.key];
        if (!asset) throw new Error("Missing resource");
        result[resource.key] = imageDataUri({ mediaType: asset.mediaType, bytes: await this.verified(asset.file) });
      }
      return result;
    });
  }
  async discard(postId: string, representationHash: string): Promise<boolean> {
    return this.serial(async () => {
      const discarded = await this.db.changeCache((data) => {
        const copy = data.copies[postId];
        if (copy && copy.representationHash !== representationHash) return false;
        data.generations[postId] = (data.generations[postId] ?? 0) + 1;
        delete data.copies[postId];
        return true;
      });
      await this.collect();
      return discarded;
    });
  }
  async invalidate(postId: string): Promise<void> {
    // This synchronous generation barrier invalidates downloads even while disk cleanup waits.
    await this.db.changeCache((data) => {
      data.generations[postId] = (data.generations[postId] ?? 0) + 1;
      data.unavailable[postId] = true;
      delete data.copies[postId];
    });
    await this.serial(() => this.collect());
  }
  async promote(content: PublicContent, assets: DownloadedAsset[], generation: number, now: string): Promise<boolean> {
    parsePublicContent(content);
    const body = new TextEncoder().encode(JSON.stringify(content));
    if (body.length > ARTICLE_BODY_BUDGET_BYTES) return false;
    const writes = new Map<string, Uint8Array>();
    const mapping: StoredCopy["assets"] = {};
    const bodyName = `b-${await this.hash(body)}`;
    writes.set(bodyName, body);
    if (assets.length !== content.resources.length) throw new Error("Incomplete resources");
    for (const resource of content.resources) {
      const asset = assets.find((item) => item.key === resource.key);
      if (
        !asset ||
        !/^[a-f0-9]{64}$/.test(asset.sha256) ||
        (await this.hash(asset.bytes)) !== asset.sha256 ||
        (resource.sha256 !== null && resource.sha256.toLowerCase() !== asset.sha256) ||
        !/^image\/(?:png|jpeg|gif|webp|svg\+xml)$/.test(asset.mediaType)
      )
        throw new Error("Invalid resource");
      const file = `a-${asset.sha256}`;
      writes.set(file, asset.bytes);
      mapping[resource.key] = { file, mediaType: asset.mediaType };
    }
    return this.serial(async () => {
      if ((await this.generation(content.postId)) !== generation || (await this.unavailable(content.postId)))
        return false;
      const disk = await this.files.list();
      const existing = new Map(disk.map((item) => [item.name, item.size]));
      let needed = 0;
      for (const [name, bytes] of writes) if (!existing.has(name)) needed += bytes.length;
      if (!(await this.makeRoom(needed, content.postId, new Set(writes.keys())))) return false;
      let committed = false;
      try {
        for (const [name, bytes] of writes) {
          if (existing.has(name)) {
            await this.verified(name);
            continue;
          }
          const stage = `stage-${++this.sequence}`;
          await this.files.write(stage, bytes);
          if ((await this.hash(await this.files.read(stage))) !== name.slice(2))
            throw new Error("Staging verification failed");
          await this.files.move(stage, name);
        }
        committed = await this.db.changeCache((data) => {
          if ((data.generations[content.postId] ?? 0) !== generation || data.unavailable[content.postId]) return false;
          data.copies[content.postId] = {
            representationHash: content.representationHash,
            body: bodyName,
            assets: mapping,
            lastRead: now,
            lastConfirmedAt: now,
          };
          return true;
        });
        // A crash here leaves only unreferenced immutable files, recovered on next start.
        await this.collect();
        return committed;
      } catch (error) {
        await this.collect().catch(() => undefined);
        if (committed) throw new CommittedCopyCleanupError(error);
        throw error;
      }
    });
  }
  private async makeRoom(needed: number, protectedId: string, reserved: ReadonlySet<string>): Promise<boolean> {
    if (needed > this.budget) return false;
    let used = (await this.files.list()).reduce((sum, file) => sum + file.size, 0);
    const data = await this.db.readCache();
    const candidates = Object.entries(data.copies)
      .filter(([id]) => id !== protectedId && !this.active.has(id))
      .sort((a, b) => a[1].lastRead.localeCompare(b[1].lastRead));
    for (const [id] of candidates) {
      if (used + needed <= this.budget) break;
      await this.db.changeCache((state) => {
        delete state.copies[id];
      });
      await this.collect(reserved);
      used = (await this.files.list()).reduce((sum, file) => sum + file.size, 0);
    }
    return used + needed <= this.budget;
  }
  async recover(): Promise<void> {
    for (const id of Object.keys((await this.db.readCache()).copies)) await this.get(id);
    await this.serial(() => this.collect());
  }
  private async collect(reserved?: ReadonlySet<string>): Promise<void> {
    const retained = referencedFiles(await this.db.readCache());
    if (reserved) for (const name of reserved) retained.add(name);
    for (const file of await this.files.list()) if (!retained.has(file.name)) await this.files.remove(file.name);
  }
}
export function referencedFiles(data: CacheData): Set<string> {
  const names = new Set<string>();
  for (const copy of Object.values(data.copies)) {
    names.add(copy.body);
    for (const asset of Object.values(copy.assets)) names.add(asset.file);
  }
  return names;
}
export function imageDataUri(asset: Pick<DownloadedAsset, "bytes" | "mediaType">): string {
  return `data:${asset.mediaType};base64,${base64(asset.bytes)}`;
}
function base64(bytes: Uint8Array): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  const chunks: string[] = [];
  let chunk = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const n = (bytes[i] << 16) | ((bytes[i + 1] ?? 0) << 8) | (bytes[i + 2] ?? 0);
    chunk +=
      alphabet[(n >>> 18) & 63] +
      alphabet[(n >>> 12) & 63] +
      (i + 1 < bytes.length ? alphabet[(n >>> 6) & 63] : "=") +
      (i + 2 < bytes.length ? alphabet[n & 63] : "=");
    if (chunk.length >= 16384) {
      chunks.push(chunk);
      chunk = "";
    }
  }
  chunks.push(chunk);
  return chunks.join("");
}
