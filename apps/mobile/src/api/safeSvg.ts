import { SaxesParser } from "saxes";
import { MobileRequestError } from "./errors";
import { assertImageBounds } from "./imageBounds";
const SVG = "http://www.w3.org/2000/svg";
const XHTML = "http://www.w3.org/1999/xhtml";
const XLINK = "http://www.w3.org/1999/xlink";
const XMLNS = "http://www.w3.org/2000/xmlns/";
const DRAWIO_HELP = "https://www.drawio.com/doc/faq/svg-export-text-problems";
const DOCTYPE = 'svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd"';
const svgTags = new Set(
  "svg g defs rect circle ellipse line polyline polygon path text tspan title desc switch foreignObject image a use symbol linearGradient radialGradient stop clipPath mask marker pattern".split(
    " ",
  ),
);
const htmlTags = new Set("div span b strong i em br p".split(" "));
const attributes = new Set(
  "id class x y x1 x2 y1 y2 cx cy r rx ry width height viewBox version preserveAspectRatio transform d points fill fill-opacity fill-rule stroke stroke-width stroke-opacity stroke-dasharray stroke-dashoffset stroke-linecap stroke-linejoin stroke-miterlimit opacity font-family font-size font-weight font-style text-anchor dominant-baseline alignment-baseline dx dy rotate textLength lengthAdjust offset stop-color stop-opacity gradientUnits gradientTransform spreadMethod fx fy clip-path clip-rule clipPathUnits mask maskUnits maskContentUnits marker-start marker-mid marker-end markerWidth markerHeight markerUnits refX refY orient patternUnits patternContentUnits patternTransform pointer-events requiredFeatures style target href".split(
    " ",
  ),
);
const cssProperties = new Set(
  "background background-color color color-scheme fill fill-opacity fill-rule stroke stroke-width stroke-opacity stroke-dasharray stroke-dashoffset stroke-linecap stroke-linejoin stroke-miterlimit opacity overflow text-align display align-items justify-content width height min-width min-height max-width max-height padding padding-top padding-right padding-bottom padding-left margin margin-top margin-right margin-bottom margin-left box-sizing font font-size font-family font-weight font-style line-height pointer-events white-space word-wrap overflow-wrap vertical-align text-decoration border border-width border-style border-color border-radius transform transform-origin clip-path mask filter".split(
    " ",
  ),
);
const unsafe = () => new MobileRequestError(0, "UNSAFE_RESOURCE", "图片未能完整保存");
const invalid = () => new MobileRequestError(0, "INVALID_RESOURCE", "图片格式无法确认");
const escapeXml = (value: string) =>
  value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/\t/g, "&#9;")
    .replace(/\n/g, "&#10;")
    .replace(/\r/g, "&#13;");
/** Parse without resolving DTDs, then emit only a passive image document. Source/cache bytes stay immutable. */
export function normalizeSvg(bytes: Uint8Array): Uint8Array {
  if (bytes.length > 8 * 1024 * 1024) throw unsafe();
  const source = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  const parser = new SaxesParser({ xmlns: true });
  const parts: string[] = [];
  const stack: { tag: string; uri: string; foreign: boolean }[] = [];
  let elements = 0,
    embeddedPixels = 0;
  const append = (value: string) => parts.push(value);
  parser.on("error", () => {
    throw unsafe();
  });
  parser.on("xmldecl", (declaration) => {
    if (declaration.version !== "1.0") throw unsafe();
  });
  parser.on("doctype", (value) => {
    if (value.trim() !== DOCTYPE) throw unsafe();
  });
  parser.on("processinginstruction", () => {
    throw unsafe();
  });
  parser.on("opentag", (tag) => {
    if (++elements > 20000 || stack.length >= 128 || Object.keys(tag.attributes).length > 64) throw unsafe();
    const parent = stack[stack.length - 1];
    const uri = tag.uri || SVG;
    if (!parent && (tag.local !== "svg" || uri !== SVG)) throw invalid();
    if (uri === SVG ? !svgTags.has(tag.local) : uri !== XHTML || !parent?.foreign || !htmlTags.has(tag.local))
      throw unsafe();
    if (!parent) svgBounds(tag.attributes);
    const attrs: string[] = [];
    const names = new Set<string>();
    for (const attribute of Object.values(tag.attributes)) {
      const { local, value } = attribute;
      if (attribute.uri === XMLNS) {
        if (![SVG, XHTML, XLINK].includes(value)) throw unsafe();
        continue;
      }
      if (attribute.uri && !(attribute.uri === XLINK && local === "href")) throw unsafe();
      if (names.has(local)) throw unsafe();
      names.add(local);
      if (!parent && local === "content") continue; // draw.io editor metadata is never rendered.
      if (!attributes.has(local) && !/^data-[a-z0-9-]+$/.test(local)) throw unsafe();
      if (local === "target") {
        if (tag.local !== "a" || value !== "_blank") throw unsafe();
        continue;
      }
      if (local === "href") {
        if (tag.local === "a" && value === DRAWIO_HELP) continue;
        if (!/^#[\w.:-]+$/.test(value)) {
          if (uri !== SVG || tag.local !== "image") throw unsafe();
          const decoded = embeddedRaster(value);
          embeddedPixels += decoded;
          if (embeddedPixels > 16_777_216) throw unsafe();
        }
      } else if (local === "style") {
        for (const declaration of value.split(";")) {
          if (!declaration.trim()) continue;
          const colon = declaration.indexOf(":");
          if (colon < 0) throw unsafe();
          const property = declaration.slice(0, colon).trim().toLowerCase();
          if (!cssProperties.has(property) || (!parent && /^(?:(?:min|max)-)?(?:width|height)$/.test(property)))
            throw unsafe();
          passiveValue(declaration.slice(colon + 1));
        }
      } else passiveValue(value);
      attrs.push(` ${local}="${escapeXml(value)}"`);
    }
    append(`<${tag.local}${parent?.uri === uri ? "" : ` xmlns="${uri}"`}${attrs.join("")}>`);
    stack.push({ tag: tag.local, uri, foreign: parent?.foreign || tag.local === "foreignObject" });
  });
  parser.on("closetag", () => {
    const tag = stack.pop()!;
    append(`</${tag.tag}>`);
  });
  parser.on("text", (value) => {
    // Character references are forbidden outside the document element, including encoded newlines.
    if (stack.length) append(escapeXml(value));
  });
  parser.on("cdata", (value) => {
    append(escapeXml(value));
  });
  parser.write(source).close();
  const result = new TextEncoder().encode(parts.join(""));
  if (result.length > 16 * 1024 * 1024) throw unsafe();
  return result;
}
function passiveValue(value: string): void {
  // Reject CSS escapes/comments before token checks; entities have already been decoded by the XML parser.
  if (/[\\@<>]|\/\*|\*\//.test(value)) throw unsafe();
  const withoutUrls = value.replace(/url\(\s*(["']?)#[\w.:-]+\1\s*\)/gi, "");
  for (const match of withoutUrls.matchAll(/([\w-]+)\s*\(/g))
    if (!/^(?:rgb|rgba|hsl|hsla|matrix|translate|scale|rotate|skewX|skewY)$/i.test(match[1])) throw unsafe();
}
function embeddedRaster(value: string): number {
  const match =
    /^data:image\/(png|jpeg|gif|webp);base64,((?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?)$/.exec(
      value,
    );
  if (!match || !match[2]) throw unsafe();
  const data = match[2],
    length = (data.length / 4) * 3 - (data.endsWith("==") ? 2 : data.endsWith("=") ? 1 : 0);
  const bytes = new Uint8Array(length),
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  for (let offset = 0, i = 0; i < data.length; i += 4) {
    const bits =
      alphabet.indexOf(data[i]) * 262144 +
      alphabet.indexOf(data[i + 1]) * 4096 +
      Math.max(0, alphabet.indexOf(data[i + 2])) * 64 +
      Math.max(0, alphabet.indexOf(data[i + 3]));
    bytes[offset++] = bits >>> 16;
    if (offset < length) bytes[offset++] = (bits >>> 8) & 255;
    if (offset < length) bytes[offset++] = bits & 255;
  }
  const ascii = (start: number, end: number) => String.fromCharCode(...bytes.subarray(start, end));
  const valid =
    match[1] === "png"
      ? [137, 80, 78, 71, 13, 10, 26, 10].every((b, i) => bytes[i] === b)
      : match[1] === "jpeg"
        ? bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255
        : match[1] === "gif"
          ? ["GIF87a", "GIF89a"].includes(ascii(0, 6))
          : ascii(0, 4) === "RIFF" && ascii(8, 12) === "WEBP";
  if (!valid) throw unsafe();
  return assertImageBounds(bytes, `image/${match[1]}`);
}

function svgBounds(attrs: Record<string, { value: string }>): void {
  const dimension = (value: string) => {
    if (!/^\s*(?:\d+(?:\.\d*)?|\.\d+)(?:px)?\s*$/.test(value)) throw unsafe();
    return Number(value.trim().replace(/px$/, ""));
  };
  const bound = (width: number, height: number) => {
    if (
      !Number.isFinite(width) ||
      !Number.isFinite(height) ||
      width <= 0 ||
      height <= 0 ||
      width > 8192 ||
      height > 8192 ||
      width * height > 16_777_216
    )
      throw unsafe();
  };
  const width = attrs.width,
    height = attrs.height;
  if (width) bound(dimension(width.value), 1);
  if (height) bound(1, dimension(height.value));
  if (width && height) bound(dimension(width.value), dimension(height.value));
  if (attrs.viewBox) {
    const values = attrs.viewBox.value
      .trim()
      .split(/[\s,]+/)
      .map(Number);
    if (values.length !== 4 || !values.every(Number.isFinite)) throw unsafe();
    bound(values[2], values[3]);
  }
}
