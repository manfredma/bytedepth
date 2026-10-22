import { assertImageBounds } from "../imageBounds";
const png = (w: number, h: number) => {
  const b = new Uint8Array(24);
  b.set([137, 80, 78, 71, 13, 10, 26, 10]);
  b.set([73, 72, 68, 82], 12);
  const v = new DataView(b.buffer);
  v.setUint32(16, w);
  v.setUint32(20, h);
  return b;
};
const gif = (w: number, h: number) => {
  const b = new Uint8Array(10);
  b.set([71, 73, 70, 56, 57, 97]);
  const v = new DataView(b.buffer);
  v.setUint16(6, w, true);
  v.setUint16(8, h, true);
  return b;
};
const jpeg = (w: number, h: number) =>
  new Uint8Array([255, 216, 255, 224, 0, 2, 255, 192, 0, 8, 8, h >> 8, h & 255, w >> 8, w & 255, 1]);
const webp = (chunk: string, w: number, h: number) => {
  const b = new Uint8Array(30);
  b.set([82, 73, 70, 70], 0);
  b.set([87, 69, 66, 80], 8);
  b.set(
    [...chunk].map((c) => c.charCodeAt(0)),
    12,
  );
  if (chunk === "VP8X") {
    b[24] = (w - 1) & 255;
    b[25] = ((w - 1) >> 8) & 255;
    b[26] = ((w - 1) >> 16) & 255;
    b[27] = (h - 1) & 255;
    b[28] = ((h - 1) >> 8) & 255;
    b[29] = ((h - 1) >> 16) & 255;
  } else if (chunk === "VP8L") {
    b[20] = 47;
    new DataView(b.buffer).setUint32(21, (w - 1) | ((h - 1) << 14), true);
  } else {
    b.set([157, 1, 42], 23);
    new DataView(b.buffer).setUint16(26, w, true);
    new DataView(b.buffer).setUint16(28, h, true);
  }
  return b;
};
it.each([
  ["image/png", png(300, 100)],
  ["image/gif", gif(300, 100)],
  ["image/jpeg", jpeg(300, 100)],
  ...["VP8X", "VP8L", "VP8 "].map((c) => ["image/webp", webp(c, 300, 100)] as [string, Uint8Array]),
] as const)("accepts bounded dimensions for %s", (type, b) =>
  expect(() => assertImageBounds(b as Uint8Array, type as string)).not.toThrow(),
);
it.each([
  ["image/png", png(0, 1)],
  ["image/png", png(9000, 1)],
  ["image/png", png(5000, 5000)],
  ["image/gif", gif(65535, 65535)],
  ["image/jpeg", jpeg(9000, 1)],
  ["image/webp", webp("VP8X", 9000, 1)],
  ["image/webp", webp("VP8L", 9000, 1)],
  ["image/webp", webp("VP8 ", 9000, 1)],
] as const)("rejects oversized or empty %s before decoding", (type, b) =>
  expect(() => assertImageBounds(b, type)).toThrow(),
);
it.each(["image/png", "image/gif", "image/jpeg", "image/webp"])("rejects truncated %s dimensions", (type) =>
  expect(() => assertImageBounds(new Uint8Array(3), type)).toThrow(),
);
it("refuses absent or corrupt JPEG frame and invalid WebP chunk", () => {
  for (const b of [
    new Uint8Array([255, 216, 255, 217]),
    new Uint8Array([255, 216, 0, 224, 0, 2]),
    new Uint8Array([255, 216, 255, 224, 0, 0]),
    new Uint8Array([255, 216, 255, 192, 0, 8]),
  ])
    expect(() => assertImageBounds(b, "image/jpeg")).toThrow();
  expect(() => assertImageBounds(webp("XXXX", 1, 1), "image/webp")).toThrow();
  const bad = webp("VP8L", 1, 1);
  bad[20] = 0;
  expect(() => assertImageBounds(bad, "image/webp")).toThrow();
  const vp = webp("VP8 ", 1, 1);
  vp[23] = 0;
  expect(() => assertImageBounds(vp, "image/webp")).toThrow();
});
it("leaves SVG passive validation to the dedicated sanitizer", () =>
  expect(() => assertImageBounds(new Uint8Array(), "image/svg+xml")).not.toThrow());
it("rejects a complete but undersized JPEG frame header", () =>
  expect(() => assertImageBounds(new Uint8Array([255, 216, 255, 192, 0, 6, 8, 0, 1, 0]), "image/jpeg")).toThrow());
