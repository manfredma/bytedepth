import { MobileRequestError } from "./errors";
/** Bound decoded allocations before invoking a native image decoder. Byte limits alone do not bound pixels. */
export function assertImageBounds(bytes: Uint8Array, type: string): number {
  if (type === "image/svg+xml") return 0;
  const invalid = () => new MobileRequestError(0, "INVALID_RESOURCE", "图片尺寸无法确认");
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const ascii = (start: number, end: number) => String.fromCharCode(...bytes.subarray(start, end));
  let width: number;
  let height: number;
  if (type === "image/png" && bytes.length >= 24 && ascii(12, 16) === "IHDR") {
    width = view.getUint32(16);
    height = view.getUint32(20);
  } else if (type === "image/gif" && bytes.length >= 10) {
    width = view.getUint16(6, true);
    height = view.getUint16(8, true);
  } else if (type === "image/webp" && bytes.length >= 30) {
    const chunk = ascii(12, 16);
    if (chunk === "VP8X") {
      width = 1 + bytes[24] + bytes[25] * 256 + bytes[26] * 65536;
      height = 1 + bytes[27] + bytes[28] * 256 + bytes[29] * 65536;
    } else if (chunk === "VP8L" && bytes[20] === 47) {
      const bits = view.getUint32(21, true);
      width = 1 + (bits & 0x3fff);
      height = 1 + ((bits >>> 14) & 0x3fff);
    } else if (chunk === "VP8 " && ascii(23, 26) === "\u009d\u0001*") {
      width = view.getUint16(26, true) & 0x3fff;
      height = view.getUint16(28, true) & 0x3fff;
    } else throw invalid();
  } else if (type === "image/jpeg") {
    let offset = 2;
    let dimensions: [number, number] | null = null;
    while (offset + 4 <= bytes.length) {
      if (bytes[offset] !== 255) throw invalid();
      const marker = bytes[offset + 1];
      const length = view.getUint16(offset + 2);
      if (length < 2 || offset + 2 + length > bytes.length) throw invalid();
      if (marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker)) {
        if (length < 8) throw invalid();
        dimensions = [view.getUint16(offset + 7), view.getUint16(offset + 5)];
        break;
      }
      offset += 2 + length;
    }
    if (!dimensions) throw invalid();
    [width, height] = dimensions;
  } else throw invalid();
  if (width < 1 || height < 1 || width > 8192 || height > 8192 || width * height > 16_777_216)
    throw new MobileRequestError(0, "RESOURCE_TOO_LARGE", "图片尺寸超过可保存范围");
  return width * height;
}
