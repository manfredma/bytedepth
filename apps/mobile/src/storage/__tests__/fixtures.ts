import { emptyCache, type CacheData, type HistoryRow, type LocalDatabase } from "../database";
import type { FileStore } from "../FileStore";
import type { PublicContent } from "../../contracts/mobile";
export class MemoryDatabase implements LocalDatabase {
  cache = emptyCache();
  rows: Record<string, HistoryRow> = {};
  failCommit = false;
  async readCache() {
    return structuredClone(this.cache);
  }
  async changeCache<T>(change: (data: CacheData) => T): Promise<T> {
    const next = structuredClone(this.cache);
    const result = change(next);
    if (this.failCommit) throw new Error("commit failed");
    this.cache = next;
    return result;
  }
  async history() {
    return structuredClone(Object.values(this.rows));
  }
  async changeHistory(postId: string, change: (row: HistoryRow | null) => HistoryRow) {
    this.rows[postId] = change(this.rows[postId] ?? null);
  }
}
export class MemoryFiles implements FileStore {
  values = new Map<string, Uint8Array>();
  failWrite = false;
  failMove = false;
  failRemove = false;
  async list() {
    return [...this.values].map(([name, bytes]) => ({ name, size: bytes.length }));
  }
  async read(name: string) {
    const bytes = this.values.get(name);
    if (!bytes) throw new Error("missing");
    return bytes.slice();
  }
  async write(name: string, bytes: Uint8Array) {
    if (this.failWrite) throw new Error("disk full");
    this.values.set(name, bytes.slice());
  }
  async move(from: string, to: string) {
    if (this.failMove) throw new Error("move failed");
    this.values.set(to, await this.read(from));
    this.values.delete(from);
  }
  async remove(name: string) {
    if (this.failRemove) throw new Error("remove failed");
    this.values.delete(name);
  }
  uri(name: string) {
    return `file:///cache/${name}`;
  }
}
export const hash = async (bytes: Uint8Array): Promise<string> => {
  let h = 0;
  for (const b of bytes) h = (Math.imul(h, 31) + b) >>> 0;
  return h.toString(16).padStart(64, "0");
};
export const now = "2026-10-22T01:00:00Z";
export function content(postId = "1", version = 1, image = false): PublicContent {
  return {
    schemaVersion: 1,
    postId,
    contentVersion: version,
    projectionVersion: 1,
    digest: `d${version}`,
    representationHash: `r${version}`,
    shellVersion: "1",
    slug: `post-${postId}`,
    title: `Title ${postId}`,
    publishedAt: now,
    category: null,
    series: null,
    html: "<p>Hello</p>",
    document: {
      projectionVersion: 1,
      digest: `d${version}`,
      text: "Hello",
      segments: [{ id: "p1", blockId: "b1", kind: "paragraph", text: "Hello", start: 0, end: 5 }],
    },
    resources: image
      ? [
          {
            key: "image",
            url: "https://images.example/a.png",
            role: "image",
            required: true,
            mediaType: "image/png",
            sha256: null,
          },
        ]
      : [],
  };
}
