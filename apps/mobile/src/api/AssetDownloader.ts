import { normalizeSvg } from "./safeSvg";
import { assertImageBounds } from "./imageBounds";
import type { PublicResource } from "../contracts/mobile";
import type { TransportOptions, TransportResponse } from "./MobileApi";
import { MobileRequestError } from "./errors";
export interface ByteReader {
  read(): Promise<{ done: boolean; value?: Uint8Array }>;
  cancel(): Promise<void>;
}
export interface AssetResponse extends TransportResponse {
  body: { getReader(): ByteReader } | null;
}
export type AssetTransport = (url: string, options: TransportOptions) => Promise<AssetResponse>;
export interface DownloadedAsset {
  key: string;
  bytes: Uint8Array;
  mediaType: string;
  sha256: string;
}
export class AssetDownloader {
  constructor(
    private readonly transport: AssetTransport,
    private readonly hash: (bytes: Uint8Array) => Promise<string>,
  ) {}
  async download(resource: PublicResource, signal?: AbortSignal): Promise<DownloadedAsset> {
    let url = safeUrl(resource.url);
    for (let redirects = 0; ; redirects++) {
      const response = await this.transport(url.href, {
        method: "GET",
        headers: { Accept: "image/*" },
        credentials: "omit",
        redirect: "manual",
        signal,
      });
      if ([301, 302, 303, 307, 308].includes(response.status)) {
        if (redirects >= 3) throw failure("RESOURCE_REDIRECT");
        const target = response.headers.get("Location");
        if (!target) throw failure("RESOURCE_REDIRECT");
        url = safeUrl(new URL(target, url).href);
        continue;
      }
      if (response.status !== 200) throw failure("RESOURCE_UNAVAILABLE");
      const mediaType = (response.headers.get("Content-Type") ?? "").split(";")[0].trim().toLowerCase();
      const bytes = await readResponseBytes(response, 8 * 1024 * 1024);
      validateImage(bytes, mediaType);
      assertImageBounds(bytes, mediaType);
      if (resource.mediaType !== null && resource.mediaType.toLowerCase() !== mediaType)
        throw failure("INVALID_RESOURCE");
      const sha256 = await this.hash(bytes);
      if (!/^[a-f0-9]{64}$/.test(sha256) || (resource.sha256 !== null && sha256 !== resource.sha256.toLowerCase()))
        throw failure("RESOURCE_DIGEST");
      return { key: resource.key, bytes, mediaType, sha256 };
    }
  }
}
export async function readResponseBytes(response: AssetResponse, limit: number): Promise<Uint8Array> {
  const declared = Number(response.headers.get("Content-Length"));
  if (Number.isFinite(declared) && declared > limit) throw failure("RESOURCE_TOO_LARGE");
  if (!response.body) throw failure("INVALID_RESOURCE");
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let length = 0;
  try {
    for (;;) {
      const next = await reader.read();
      if (next.done) break;
      if (!next.value) throw failure("INVALID_RESOURCE");
      length += next.value.length;
      if (length > limit) throw failure("RESOURCE_TOO_LARGE");
      chunks.push(next.value);
    }
  } catch (error) {
    await reader.cancel().catch(() => undefined);
    throw error;
  }
  const result = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.length;
  }
  return result;
}
function safeUrl(value: string): URL {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw failure("UNSAFE_RESOURCE");
  }
  if (url.protocol !== "https:" || url.username || url.password) throw failure("UNSAFE_RESOURCE");
  return url;
}
function failure(code: string): MobileRequestError {
  return new MobileRequestError(0, code, "图片未能完整保存");
}
function validateImage(bytes: Uint8Array, type: string): void {
  const prefix = Array.from(bytes.subarray(0, 12));
  const ascii = (start: number, end: number) => String.fromCharCode(...bytes.subarray(start, end));
  if (type === "image/png" && [137, 80, 78, 71, 13, 10, 26, 10].every((value, index) => prefix[index] === value))
    return;
  if (type === "image/jpeg" && prefix[0] === 255 && prefix[1] === 216 && prefix[2] === 255) return;
  if (type === "image/gif" && ["GIF87a", "GIF89a"].includes(ascii(0, 6))) return;
  if (type === "image/webp" && ascii(0, 4) === "RIFF" && ascii(8, 12) === "WEBP") return;
  if (type === "image/svg+xml") {
    normalizeSvg(bytes);
    return;
  }
  throw failure("INVALID_RESOURCE");
}
