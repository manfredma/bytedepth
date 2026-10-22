import { SaxesParser } from "saxes";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { AssetDownloader, type AssetResponse } from "../AssetDownloader";
import { imageDataUri, CacheIndex } from "../../storage/CacheIndex";
import { content, MemoryDatabase, MemoryFiles, hash, now } from "../../storage/__tests__/fixtures";
const fixture = readFileSync(join(__dirname, "fixtures/drawio-passive.svg"), "utf8");
const encode = (text: string) => new TextEncoder().encode(text);
const resource = content("1", 1, true).resources[0];
function download(svg: string) {
  let done = false;
  const bytes = encode(svg);
  const response: AssetResponse = {
    status: 200,
    headers: { get: (name) => (name === "Content-Type" ? "image/svg+xml" : null) },
    text: async () => "",
    body: {
      getReader: () => ({
        read: async () => (done ? { done: true } : { done: false, value: ((done = true), bytes) }),
        cancel: async () => undefined,
      }),
    },
  };
  return new AssetDownloader(async () => response, hash).download({ ...resource, mediaType: null, sha256: null });
}
const rendered = (bytes: Uint8Array) =>
  Buffer.from(imageDataUri({ bytes, mediaType: "image/svg+xml" }).split(",")[1], "base64").toString("utf8");
it("accepts an inert draw.io export while retaining original digest-bound cache bytes", async () => {
  const asset = await download(fixture);
  expect(asset.bytes).toEqual(encode(fixture));
  expect(asset.sha256).toBe(await hash(encode(fixture)));
  const c = content("1", 1, true);
  c.resources[0].sha256 = asset.sha256;
  const files = new MemoryFiles(),
    index = new CacheIndex(new MemoryDatabase(), files, hash);
  expect(await index.promote(c, [asset], 0, now)).toBe(true);
  expect(files.values.get(`a-${asset.sha256}`)).toEqual(encode(fixture));
  const uri = (await index.assetDataUris(c))[resource.key];
  const svg = Buffer.from(uri.split(",")[1], "base64").toString("utf8");
  expect(svg).not.toMatch(/<!|content=|https:\/\/www.drawio.com/);
  expect(svg).toContain("规则 &amp;");
  expect(svg).toContain("data:image/png;base64,");
  expect(svg).toContain("foreignObject");
});
it("normalizes before any SVG data image is exposed, including a previously stored source", () => {
  expect(rendered(encode(fixture))).not.toMatch(/<!|<\?xml|content=/);
});
it.each([
  "<svg><g></svg>",
  "<svg/><svg/>",
  '<svg><path a="1" a="2"/></svg>',
  '<svg><path fill="&unknown;"/></svg>',
  '<!DOCTYPE svg [<!ENTITY e "x">]><svg>&e;</svg>',
  '<!DOCTYPE svg SYSTEM "https://evil.invalid/a"><svg/>',
  '<svg xmlns:x="https://evil.invalid/ns"><x:script/></svg>',
  '<svg xmlns:x="http://www.w3.org/1999/xhtml"><x:script/></svg>',
  '<svg><image href="data:image/svg+xml;base64,PHN2Zy8+"/></svg>',
  '<svg><image href="data:image/png;base64,YmFk"/></svg>',
  '<svg><rect style="fill:u&#114;l(https://evil.invalid/x)"/></svg>',
  '<svg><rect style="fill:u\\72l(https://evil.invalid/x)"/></svg>',
  '<svg><rect style="fill:u/**/rl(https://evil.invalid/x)"/></svg>',
  '<svg><use href="&#104;ttps://evil.invalid/x"/></svg>',
  '<svg><animate attributeName="href" to="https://evil.invalid/x"/></svg>',
  '<svg><foreignObject><div xmlns="http://www.w3.org/1999/xhtml" style="background-image:image-set(\'https://evil.invalid/x\' 1x)"/></foreignObject></svg>',
  `<svg>${"<g>".repeat(130)}${"</g>".repeat(130)}</svg>`,
  `<svg>${"<path/>".repeat(20001)}</svg>`,
  `<svg ${Array.from({ length: 65 }, (_, i) => `data-a${i}="a"`).join(" ")}/>`,
])("rejects malformed, active or unbounded XML %s", async (svg) => {
  await expect(download(svg)).rejects.toBeDefined();
  expect(() => rendered(encode(svg))).toThrow();
});

it("bounds total embedded raster pixels even when every image individually fits", async () => {
  const png = Buffer.alloc(24);
  png.set([137, 80, 78, 71, 13, 10, 26, 10]);
  png.write("IHDR", 12);
  png.writeUInt32BE(4096, 16);
  png.writeUInt32BE(4096, 20);
  await expect(
    download(
      `<svg><image href="data:image/png;base64,${png.toString("base64")}"/><image href="data:image/png;base64,${png.toString("base64")}"/></svg>`,
    ),
  ).rejects.toBeDefined();
});
it.each([
  'width="9000" height="1"',
  'width="5000" height="5000"',
  'viewBox="0 0 9000 1"',
  'width="0" height="1"',
  'viewBox="0 0 -1 1"',
])("bounds SVG dimensions %s", async (attrs) => {
  await expect(download(`<svg ${attrs}/>`)).rejects.toBeDefined();
});
it.each([
  '<?xml-stylesheet href="https://evil.invalid/x"?><svg/>',
  '<svg xmlns="http://www.w3.org/1999/xhtml"/>',
  '<svg xmlns:xlink="http://www.w3.org/1999/xlink" href="#a" xlink:href="#b"/>',
  '<svg xml:lang="en"/>',
  '<svg xmlns:xlink="http://www.w3.org/1999/xlink" xlink:title="x"/>',
  '<svg><div xmlns="http://www.w3.org/1999/xhtml"/></svg>',
  '<svg><foreignObject><img xmlns="http://www.w3.org/1999/xhtml"/></foreignObject></svg>',
  '<svg><rect target="_blank"/></svg>',
  '<svg><a target="_self"/></svg>',
  '<svg><rect style="fill"/></svg>',
  '<svg><image href="data:image/png;base64,"/></svg>',
  '<svg width="100%"/>',
  '<svg height="0"/>',
  '<svg height="9000"/>',
  '<svg viewBox="0 0 1"/>',
  '<svg viewBox="0 0 NaN 1"/>',
  `<svg width="${"9".repeat(400)}"/>`,
  `<svg height="${"9".repeat(400)}"/>`,
])("rejects unsupported XML attribute/style/geometry %#", async (svg) => {
  await expect(download(svg)).rejects.toBeDefined();
});
it("supports decoded labels, namespace aliases, local refs and passive CSS functions", async () => {
  const svg =
    '<s:svg xmlns:s="http://www.w3.org/2000/svg" width=".5px" height="2" viewBox="0,0,2,2"><s:text><![CDATA[A < B & "label"]]></s:text><s:rect fill="rgb(1,2,3)" style="fill:url(\'\u0023a\'); transform: matrix(1,0,0,1,0,0);"/><s:use href="#a"/></s:svg>';
  const normalized = rendered((await download(svg)).bytes);
  expect(normalized).toContain('xmlns="http://www.w3.org/2000/svg"');
  expect(normalized).toContain("A &lt; B &amp; &quot;label&quot;");
  expect(normalized).toContain("rgb(1,2,3)");
  expect(normalized).toContain('href="#a"');
});
it("bounds source and normalized output independently", () => {
  expect(() => rendered(new Uint8Array(8 * 1024 * 1024 + 1))).toThrow();
  expect(() => rendered(encode(`<svg><text>${'"'.repeat(3 * 1024 * 1024)}</text></svg>`))).toThrow();
  expect(() => rendered(new Uint8Array([255, 255]))).toThrow();
});
const embedded = (type: string, bytes: Uint8Array) =>
  `<svg><image href="data:image/${type};base64,${Buffer.from(bytes).toString("base64")}"/></svg>`;
const boundedPng = (length: number) => {
  const bytes = Buffer.alloc(length);
  bytes.set([137, 80, 78, 71, 13, 10, 26, 10]);
  bytes.write("IHDR", 12);
  bytes.writeUInt32BE(1, 16);
  bytes.writeUInt32BE(1, 20);
  return bytes;
};
it.each([
  ["png", boundedPng(24)],
  ["png", boundedPng(25)],
  ["png", boundedPng(26)],
  ["jpeg", new Uint8Array([255, 216, 255, 192, 0, 8, 8, 0, 1, 0, 1, 1])],
  ["gif", new Uint8Array([71, 73, 70, 56, 57, 97, 1, 0, 1, 0])],
  [
    "webp",
    new Uint8Array([
      82, 73, 70, 70, 0, 0, 0, 0, 87, 69, 66, 80, 86, 80, 56, 88, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    ]),
  ],
] as const)("accepts embedded bounded %s with canonical base64 padding %#", async (type, bytes) => {
  expect((await download(embedded(type, bytes))).mediaType).toBe("image/svg+xml");
});
it.each([
  ["jpeg", [1, 2, 3]],
  ["jpeg", [255, 1, 3]],
  ["jpeg", [255, 216, 1]],
  ["gif", [1, 2, 3]],
  ["webp", [1, 2, 3]],
  ["webp", [82, 73, 70, 70, 0, 0, 0, 0, 1, 2, 3]],
] as const)("rejects embedded %s with lying byte signature %#", async (type, bytes) => {
  await expect(download(embedded(type, new Uint8Array(bytes)))).rejects.toBeDefined();
});

it("serializes valid standalone SVG XML across document prolog and trailing whitespace", () => {
  for (const source of [fixture, '\n<svg xmlns="http://www.w3.org/2000/svg"><text> A\n B </text></svg>\n']) {
    const normalized = rendered(encode(source));
    expect(() => new SaxesParser({ xmlns: true }).write(normalized).close()).not.toThrow();
    expect(normalized).toMatch(/^<svg[\s>]/);
    expect(normalized).toMatch(/<\/svg>$/);
  }
  expect(rendered(encode("<svg><text> A\n B </text></svg>"))).toContain(" A&#10; B ");
});
it.each(["width", "height", "min-width", "min-height", "max-width", "max-height"])(
  "rejects root CSS %s overrides while retaining foreignObject label layout",
  async (property) => {
    await expect(download(`<svg width="1" height="1" style="${property}:1000000000px"/>`)).rejects.toBeDefined();
    expect(
      (
        await download(
          `<svg><foreignObject><div xmlns="http://www.w3.org/1999/xhtml" style="${property}:100px"/></foreignObject></svg>`,
        )
      ).mediaType,
    ).toBe("image/svg+xml");
  },
);
it("rejects XML 1.1 instead of serializing its controls into an XML 1.0 image", async () => {
  const svg = '<?xml version="1.1"?><svg><text>&#1;</text></svg>';
  await expect(download(svg)).rejects.toBeDefined();
  expect(() => rendered(encode(svg))).toThrow();
});
