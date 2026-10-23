/** @jest-environment jsdom */
import fs from "node:fs";
import path from "node:path";
import { TextEncoder } from "node:util";
const repository = path.resolve(__dirname, "../../../../../");
const read = (p: string) => fs.readFileSync(path.join(repository, p), "utf8");
beforeAll(() => {
  Object.defineProperty(Range.prototype, "getClientRects", { configurable: true, writable: true, value: () => [] });
});
const staticRoot = "bytedepth-start/src/main/resources/static/";
function boot(name = "rich", extraHtml = "", imageDataUris: Record<string, string> = {}, settings: any = {}) {
  document.body.innerHTML =
    '<article id="post-article"><div class="content"></div></article><div id="reader-marks"></div><dialog id="reader-preview"></dialog>';
  (window as any).MobileReader?.dispose();
  const messages: any[] = [];
  const w = window as any;
  w.TextEncoder = TextEncoder;
  w.ReactNativeWebView = { postMessage: (raw: string) => messages.push(JSON.parse(raw)) };
  w.scrollTo = jest.fn();
  w.scrollBy = jest.fn();
  w.requestAnimationFrame = (fn: () => void) => {
    fn();
    return 1;
  };
  w.eval(read(staticRoot + "vendor/hypothesis-anchoring/trim-range.js"));
  w.eval(read(staticRoot + "vendor/hypothesis-anchoring/text-range.js"));
  w.eval(read(staticRoot + "js/annotation-document.js"));
  jest.resetModules();
  // The CommonJS load instruments the exact browser runtime, without a second test implementation.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  require("../web/reader");
  const model = JSON.parse(read(`scripts/test-fixtures/annotation/${name}-document.json`));
  const content = {
    ...model,
    postId: "1",
    contentVersion: 7,
    document: model,
    html: read(`scripts/test-fixtures/annotation/${name}-page.html`) + extraHtml,
    resources: [{ key: "image", url: "image.png" }],
  };
  w.MobileReader.mount({
    siteOrigin: "https://staging-bytedepth.bytedepth.cn",
    content,
    generation: 3,
    imageDataUris,
    resume: { kind: "start", target: null },
    annotations: null,
    theme: "light",
    fontScale: 1,
    ...settings,
  });
  w.MobileReader.start();
  return { w, messages, model, root: document.querySelector(".content")! };
}
afterEach(() => {
  (window as any).MobileReader?.dispose();
  jest.restoreAllMocks();
  jest.useRealTimers();
  delete (window as any).ResizeObserver;
  delete (document as any).fonts;
  delete (document as any).caretRangeFromPoint;
});
test.each(["rich", "unicode", "duplicates", "footnotes"])(
  "canonical %s projection retains codepoint mappings and virtual separators",
  (name) => {
    const { w, model, root, messages } = boot(name);
    expect([...root.querySelectorAll("[data-annotation-segment]")].map((e) => e.textContent)).toEqual(
      model.segments.map((s: any) => s.text),
    );
    const range = document.createRange();
    range.selectNodeContents(root);
    w.getSelection().removeAllRanges();
    w.getSelection().addRange(range);
    document.dispatchEvent(new Event("selectionchange"));
    expect(messages.find((m) => m.type === "selection")?.payload.exact).toBe(model.text);
    expect(messages.find((m) => m.type === "ready")).toMatchObject({ generation: 3, postId: "1", contentVersion: 7 });
  },
);
test("authored scripts, frames, SVG, events, CSS and image URLs cannot reach the live DOM", () => {
  const { root, messages } = boot(
    "rich",
    '<script>window.stolen=1</script><iframe src="https://evil"></iframe><svg onload="alert(1)"><image href="https://evil"/></svg><form action="https://evil"><input></form><img src="https://evil" onerror="alert(1)"><a href="file:///token">file</a><style>@import "https://evil";</style><p style="background:url(https://evil)">safe</p>',
  );
  expect(root.querySelector("script,iframe,svg,form,input,style,[onerror],[style]")).toBeNull();
  expect(root.querySelectorAll("img")).toHaveLength(0);
  expect(root.querySelectorAll(".reader-image-missing")).toHaveLength(2);
  root.querySelector("a")!.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
  expect(messages.some((m) => m.type === "link")).toBe(false);
});
test("only supplied validated data images load, actual decode error is reported, preview stays passive", () => {
  const { root, messages } = boot("rich", "", { image: "data:image/png;base64,aGVsbG8=" });
  const image = root.querySelector("img")!;
  expect(image.getAttribute("src")).toBe("data:image/png;base64,aGVsbG8=");
  image.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  expect(document.querySelector("#reader-preview img")?.getAttribute("src")).toBe(image.getAttribute("src"));
  image.dispatchEvent(new Event("error"));
  expect(messages.find((m) => m.type === "renderError")?.payload.message).toBe("IMAGE_DECODE_FAILED:image");
  expect(root.querySelector("img")).toBeNull();
});
test("canonical code mapping survives real Prism enhancement and cross-block separators", () => {
  const { w, messages, root } = boot();
  w.Prism = { manual: true };
  w.eval(read(staticRoot + "vendor/prism/prism-common.min.js"));
  w.eval(read(staticRoot + "js/code-blocks.js"));
  const first = root.querySelector('[data-annotation-segment="s1"]')!;
  const last = root.querySelector('[data-annotation-segment="s7"]')!;
  const range = document.createRange();
  range.setStart(first.firstChild!, 0);
  range.setEnd(last.firstChild!, 1);
  w.getSelection().removeAllRanges();
  w.getSelection().addRange(range);
  document.dispatchEvent(new Event("selectionchange"));
  const payload = messages.filter((m) => m.type === "selection").at(-1).payload;
  expect(payload.exact).toBe("前文重点与inline。\nint anchorProbe = 1;\nA");
  expect(payload.spans.find((s: any) => s.segmentId === "s6")).toEqual({ segmentId: "s6", start: 0, end: 21 });
});

test("same-version exact anchor survives image/font/graph reflow and stops during intentional scroll", async () => {
  jest.useFakeTimers();
  let top = 240;
  const rectangles = jest
    .spyOn(Range.prototype, "getClientRects")
    .mockImplementation(() => [{ top, bottom: top + 20, left: 10, width: 40 }] as any);
  let resize: () => void = () => {};
  const disconnected = jest.fn();
  (window as any).ResizeObserver = class {
    constructor(callback: () => void) {
      resize = callback;
    }
    observe() {}
    disconnect() {
      disconnected();
    }
  };
  Object.defineProperty(document, "fonts", { configurable: true, value: { ready: Promise.resolve() } });
  const { w, root, messages, model } = boot(
    "rich",
    "",
    { image: "data:image/png;base64,YQ==" },
    { resume: { kind: "exact", target: { segmentId: "s1", codePointOffset: 1, viewportRatio: 0.25 } } },
  );
  expect(w.scrollBy).toHaveBeenCalledWith(0, top - window.innerHeight * 0.25);
  w.scrollBy.mockClear();
  top = 440;
  root.querySelector("img")!.dispatchEvent(new Event("load"));
  root.dispatchEvent(new Event("bd:content-rendered"));
  root.dispatchEvent(new Event("bd:content-geometry"));
  window.dispatchEvent(new Event("resize"));
  resize();
  await Promise.resolve();
  expect(w.scrollBy).toHaveBeenCalledTimes(6);
  w.scrollBy.mockClear();
  window.dispatchEvent(new Event("touchstart"));
  top = 900;
  root.dispatchEvent(new Event("bd:content-rendered"));
  expect(w.scrollBy).not.toHaveBeenCalled();
  const node = root.querySelector('[data-annotation-segment="s1"]')!.firstChild!;
  w.document.caretRangeFromPoint = () => {
    const range = document.createRange();
    range.setStart(node, 1);
    range.collapse(true);
    return range;
  };
  window.dispatchEvent(new Event("scroll"));
  jest.advanceTimersByTime(160);
  expect(messages.filter((m) => m.type === "position").at(-1).payload).toMatchObject({
    segmentId: "s1",
    codePointOffset: 1,
    blockText: "前文重点与inline。",
    precedingHeadings: ["阅读"],
  });
  w.document.caretRangeFromPoint = () => null;
  w.MobileReader.dispose();
  expect(disconnected).toHaveBeenCalledTimes(1);
  resize();
  root.dispatchEvent(new Event("bd:content-rendered"));
  expect(model.text).toContain("\t");
  delete w.ResizeObserver;
  delete w.document.fonts;
  delete w.document.caretRangeFromPoint;
  rectangles.mockRestore();
  jest.useRealTimers();
});

test("matching annotations draw only verified ranges; mismatched projection/version/digest and unanchored records never draw", () => {
  const rectangles = jest
    .spyOn(Range.prototype, "getClientRects")
    .mockReturnValue([{ top: 1, bottom: 20, left: 4, width: 25 }] as any);
  const model = JSON.parse(read("scripts/test-fixtures/annotation/rich-document.json"));
  const annotation = {
    targetDigest: model.digest,
    color: "red",
    anchor: { exact: "前文" },
    placement: {
      status: "ANCHORED",
      targetRevision: 7,
      projectionVersion: 1,
      spans: [{ segmentId: "s1", start: 0, end: 2 }],
    },
  };
  const envelope = {
    postId: "1",
    contentVersion: 7,
    projectionVersion: 1,
    digest: model.digest,
    annotations: [annotation],
  };
  for (const change of [{ postId: "2" }, { contentVersion: 8 }, { projectionVersion: 2 }, { digest: "wrong" }]) {
    boot("rich", "", {}, { annotations: { ...envelope, ...change } });
    expect(document.querySelector(".reader-mark")).toBeNull();
  }
  boot("rich", "", {}, { annotations: { ...envelope, annotations: [{ ...annotation, anchor: { exact: "错误" } }] } });
  expect(document.querySelector(".reader-mark")).toBeNull();
  boot(
    "rich",
    "",
    {},
    { annotations: { ...envelope, annotations: [annotation, { ...annotation, color: "unknown" }] } },
  );
  expect(document.querySelectorAll(".reader-mark")).toHaveLength(2);
  expect(document.querySelector(".reader-mark--red")?.getAttribute("style")).toContain("width: 25px");
  expect(document.querySelector(".reader-mark--yellow")).not.toBeNull();
  (window as any).MobileReader.dispose();
  expect(document.querySelector(".reader-mark")).toBeNull();
  rectangles.mockRestore();
});

test("links and footnotes are explicit, preview closes, oversized selections and unmapped source are ignored", () => {
  const { w, root, messages } = boot(
    "rich",
    '<a href="https://example.com/path">外链</a><a href="#阅读">目录</a><a href="#missing">缺失脚注</a><a href="https://user:secret@evil.test/">账号外链</a>',
    { image: "data:image/svg+xml;base64,PHN2Zy8+" },
  );
  const scroll = jest.fn();
  (root.querySelector("h1") as any).scrollIntoView = scroll;
  const links = root.querySelectorAll("a");
  for (const link of links) link.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
  expect(messages.filter((m) => m.type === "link").map((m) => m.payload.href)).toEqual(["https://example.com/path"]);
  expect(scroll).toHaveBeenCalledTimes(1);
  const image = root.querySelector("img")!;
  image.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  document.querySelector("#reader-preview img")!.dispatchEvent(new Event("error"));
  expect(messages.filter((m) => m.type === "renderError").at(-1).payload.message).toBe("IMAGE_DECODE_FAILED:image");
  document.querySelector("#reader-preview")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  expect(document.querySelector("#reader-preview")!.hasAttribute("open")).toBe(false);
  w.getSelection().removeAllRanges();
  document.dispatchEvent(new Event("selectionchange"));
  expect(root.querySelector("img")!.namespaceURI).toBe("http://www.w3.org/1999/xhtml");
});
test("missing/empty/Mermaid/invalid mappings fail closed; segment end positions and valid heading fallback restore", () => {
  const rects = jest
    .spyOn(Range.prototype, "getClientRects")
    .mockReturnValue([{ top: 40, bottom: 60, left: 0, width: 10 }] as any);
  for (const target of [
    { segmentId: "missing", codePointOffset: 0 },
    { segmentId: "s0", codePointOffset: 2 },
  ]) {
    const { w } = boot("rich", "", {}, { resume: { kind: "exact", target: { ...target, viewportRatio: 0 } } });
    if (target.segmentId === "missing") expect(w.scrollBy).not.toHaveBeenCalled();
    else expect(w.scrollBy).toHaveBeenCalledWith(0, 40);
  }
  const { w: heading } = boot(
    "rich",
    '<h2>未映射标题</h2><table><tr><td colspan="2" rowspan="1">表格</td></tr></table><a href="https://[">bad url</a><p id="bad id">bad id</p>',
    {},
    { resume: { kind: "heading", target: { segmentId: "s0", codePointOffset: 0, viewportRatio: 0 } } },
  );
  expect(heading.scrollBy).toHaveBeenCalled();
  const { w: rejected } = boot(
    "rich",
    "",
    {},
    { resume: { kind: "heading", target: { segmentId: "s1", codePointOffset: 0, viewportRatio: 0 } } },
  );
  expect(rejected.scrollBy).not.toHaveBeenCalled();
  boot("rich", "", {}, { resume: { kind: "heading", target: null } });
  const model = JSON.parse(read("scripts/test-fixtures/annotation/rich-document.json"));
  for (const kind of ["mermaid", "prose"]) {
    const custom = {
      ...model,
      postId: "1",
      contentVersion: 7,
      document: { ...model, segments: model.segments.map((s: any, i: number) => (i ? s : { ...s, kind, text: "" })) },
      html: read("scripts/test-fixtures/annotation/rich-page.html"),
      resources: [],
    };
    const { w } = boot(
      "rich",
      "",
      {},
      { content: custom, resume: { kind: "exact", target: { segmentId: "s0", codePointOffset: 0, viewportRatio: 0 } } },
    );
    expect(w.scrollBy).not.toHaveBeenCalled();
  }
  const { w: badMap } = boot(
    "rich",
    "",
    {},
    {
      content: {
        ...model,
        postId: "1",
        contentVersion: 7,
        document: { ...model, digest: "bad" },
        html: read("scripts/test-fixtures/annotation/rich-page.html"),
        resources: [],
      },
      resume: { kind: "exact", target: { segmentId: "s0", codePointOffset: 0, viewportRatio: 0 } },
    },
  );
  expect(badMap.scrollBy).not.toHaveBeenCalled();
  rects.mockRestore();
});

test("oversized TOC/selection do not cross bridge; fallback works without browser geometry support", () => {
  const extra = Array.from(
    { length: 100 },
    (_, i) => `<h2><span data-annotation-segment="s${i + 999}">${"长".repeat(300)}</span></h2>`,
  ).join("");
  const { messages } = boot("rich", extra);
  expect(messages.some((m) => m.type === "toc")).toBe(false);
  const huge = "😀".repeat(501);
  const model = {
    projectionVersion: 1,
    digest: "a".repeat(64),
    text: huge,
    segments: [{ id: "s0", blockId: "b0", kind: "prose", text: huge, start: 0, end: 501 }],
  };
  const {
    w,
    root,
    messages: second,
  } = boot(
    "rich",
    "",
    {},
    {
      content: {
        ...model,
        postId: "1",
        contentVersion: 7,
        document: model,
        html: `<span data-annotation-segment="s0">${huge}</span>`,
        resources: [],
      },
    },
  );
  const selection = w.getSelection();
  const range = document.createRange();
  range.selectNodeContents(root);
  selection.removeAllRanges();
  selection.addRange(range);
  document.dispatchEvent(new Event("selectionchange"));
  expect(second.some((m) => m.type === "selection")).toBe(false);
  const old = Range.prototype.getClientRects;
  Object.defineProperty(Range.prototype, "getClientRects", { configurable: true, writable: true, value: undefined });
  const a = {
    targetDigest: model.digest,
    color: "blue",
    anchor: { exact: huge },
    placement: {
      status: "ANCHORED",
      targetRevision: 7,
      projectionVersion: 1,
      spans: [{ segmentId: "s0", start: 0, end: 501 }],
    },
  };
  boot(
    "rich",
    "",
    {},
    {
      content: {
        ...model,
        postId: "1",
        contentVersion: 7,
        document: model,
        html: `<span data-annotation-segment="s0">${huge}</span>`,
        resources: [],
      },
      resume: { kind: "exact", target: { segmentId: "s0", codePointOffset: 0, viewportRatio: 0 } },
      annotations: { postId: "1", contentVersion: 7, projectionVersion: 1, digest: model.digest, annotations: [a] },
    },
  );
  expect(document.querySelector(".reader-mark")).toBeNull();
  Object.defineProperty(Range.prototype, "getClientRects", { configurable: true, writable: true, value: old });
});

test("caret codepoint conversion rejects a surrogate midpoint and preserves emoji start; captured callbacks cannot revive disposed reader", async () => {
  jest.useFakeTimers();
  let savedError: EventListener | undefined;
  let savedScroll: EventListener | undefined;
  const add = EventTarget.prototype.addEventListener;
  const spy = jest.spyOn(EventTarget.prototype, "addEventListener").mockImplementation(function (
    this: EventTarget,
    name,
    callback,
    options,
  ) {
    if ((this as Element).tagName === "IMG" && name === "error") savedError = callback as EventListener;
    if (this === window && name === "scroll") savedScroll = callback as EventListener;
    return add.call(this, name, callback, options);
  });
  const windowAdd = window.addEventListener;
  jest.spyOn(window, "addEventListener").mockImplementation((name, callback, options) => {
    if (name === "scroll") savedScroll = callback as EventListener;
    return windowAdd.call(window, name, callback, options);
  });
  const { w, root, messages } = boot("unicode", '<img src="image.png">', { image: "data:image/png;base64,YQ==" });
  const node = root.querySelector('[data-annotation-segment="s0"]')!.firstChild!;
  let offset = 1;
  w.document.caretRangeFromPoint = () => {
    const r = document.createRange();
    r.setStart(node, offset);
    r.collapse(true);
    return r;
  };
  window.dispatchEvent(new Event("wheel"));
  jest.advanceTimersByTime(160);
  expect(messages.filter((m) => m.type === "position").at(-1).payload.codePointOffset).toBe(1);
  offset = 2;
  window.dispatchEvent(new Event("keydown"));
  jest.advanceTimersByTime(160);
  // Mid-surrogate is rejected by the canonical mapper; no unsafe coordinate is emitted.
  expect(messages.filter((m) => m.type === "position").at(-1).payload.codePointOffset).not.toBe(2);
  const count = messages.length;
  w.MobileReader.dispose();
  savedError!(new Event("error"));
  savedScroll!(new Event("scroll"));
  w.MobileReader.start();
  expect(messages).toHaveLength(count);
  delete w.document.caretRangeFromPoint;
  spy.mockRestore();
  jest.useRealTimers();
});
test("native layer/theme updates preserve DOM/anchor, clear private marks immediately and ignore disposed updates", async () => {
  jest.useFakeTimers();
  const rects = jest
    .spyOn(Range.prototype, "getClientRects")
    .mockReturnValue([{ top: 0, bottom: 20, left: 0, width: 10 }] as any);
  let fontsResolve: () => void = () => {};
  Object.defineProperty(document, "fonts", {
    configurable: true,
    value: {
      ready: new Promise<void>((resolve) => {
        fontsResolve = resolve;
      }),
    },
  });
  const { w, root, model } = boot();
  const original = root.firstChild;
  const annotation = {
    targetDigest: model.digest,
    color: "green",
    anchor: { exact: "前文" },
    placement: {
      status: "ANCHORED",
      targetRevision: 7,
      projectionVersion: 1,
      spans: [{ segmentId: "s1", start: 0, end: 2 }],
    },
  };
  w.MobileReader.setAnnotations({
    postId: "1",
    contentVersion: 7,
    projectionVersion: 1,
    digest: model.digest,
    annotations: [annotation],
  });
  expect(document.querySelector(".reader-mark--green")).not.toBeNull();
  w.MobileReader.configure({ theme: "dark", fontScale: 1.4 });
  expect(root.firstChild).toBe(original);
  expect(document.documentElement.dataset.theme).toBe("midnight");
  w.MobileReader.setAnnotations(null);
  expect(document.querySelector(".reader-mark")).toBeNull();
  window.dispatchEvent(new Event("scroll"));
  rects.mockReturnValue([] as any);
  window.dispatchEvent(new Event("scroll"));
  w.MobileReader.configure({ theme: "light", fontScale: 1 });
  // Simulate callbacks already captured before disposal; cancellation alone is not the guard.
  const timeout = jest.spyOn(window, "setTimeout");
  window.dispatchEvent(new Event("touchstart"));
  const settle = timeout.mock.calls.at(-1)![0] as () => void;
  w.MobileReader.dispose();
  settle();
  fontsResolve();
  await Promise.resolve();
  w.MobileReader.setAnnotations({});
  w.MobileReader.configure({ theme: "dark", fontScale: 2 });
  expect(document.documentElement.dataset.theme).toBe("daylight");
  rects.mockRestore();
});
test("canonical Mermaid renderer preserves source and falls back visibly on failure without affecting neighboring selections", async () => {
  const source = "graph TD\nA-->B";
  const model = {
    projectionVersion: 1,
    digest: "a".repeat(64),
    text: `前文\n${source}\n后文`,
    segments: [
      { id: "s0", blockId: "b0", kind: "prose", text: "前文", start: 0, end: 2 },
      { id: "s1", blockId: "b1", kind: "mermaid", text: source, start: 3, end: 3 + source.length },
      { id: "s2", blockId: "b2", kind: "prose", text: "后文", start: 4 + source.length, end: 6 + source.length },
    ],
  };
  const html =
    '<p><span data-annotation-segment="s0">前文</span></p><pre><code class="language-mermaid" data-annotation-segment="s1">graph TD\nA--&gt;B</code></pre><p><span data-annotation-segment="s2">后文</span></p>';
  const { w, root, messages } = boot(
    "rich",
    "",
    {},
    { content: { ...model, document: model, postId: "1", contentVersion: 7, html, resources: [] } },
  );
  w.mermaid = { initialize: jest.fn(), run: jest.fn().mockRejectedValue(new Error("invalid chart")) };
  w.eval(read(staticRoot + "js/post-mermaid.js"));
  w.initPostMermaid();
  for (let i = 0; i < 5; i++) await Promise.resolve();
  expect(root.querySelector("pre code")!.textContent).toBe(source);
  expect(root.querySelector(".bd-mermaid-error")!.textContent).toContain("源码");
  const range = document.createRange();
  range.selectNodeContents(root.querySelector('[data-annotation-segment="s2"]')!);
  w.getSelection().removeAllRanges();
  w.getSelection().addRange(range);
  document.dispatchEvent(new Event("selectionchange"));
  expect(messages.filter((m) => m.type === "selection").at(-1).payload.exact).toBe("后文");
  range.selectNodeContents(root.querySelector('[data-annotation-segment="s1"]')!);
  w.getSelection().removeAllRanges();
  w.getSelection().addRange(range);
  document.dispatchEvent(new Event("selectionchange"));
  expect(messages.filter((m) => m.type === "selection")).toHaveLength(1);
});
test("verified images arrive in-place without replacing article or anchor; wrong generation and failed bytes never revive an image", () => {
  const rects = jest
    .spyOn(Range.prototype, "getClientRects")
    .mockReturnValue([{ top: 100, bottom: 120, left: 0, width: 20 }] as any);
  let imageError: EventListener | undefined;
  let previewError: EventListener | undefined;
  const add = EventTarget.prototype.addEventListener;
  jest.spyOn(EventTarget.prototype, "addEventListener").mockImplementation(function (
    this: EventTarget,
    type,
    callback,
    options,
  ) {
    if ((this as Element).tagName === "IMG" && type === "error") {
      if (!imageError) imageError = callback as EventListener;
      else previewError = callback as EventListener;
    }
    return add.call(this, type, callback, options);
  });
  const { w, root, messages } = boot(
    "rich",
    "",
    {},
    { resume: { kind: "exact", target: { segmentId: "s1", codePointOffset: 1, viewportRatio: 0.25 } } },
  );
  const first = root.firstChild;
  const context = { postId: "1", contentVersion: 7, generation: 3 };
  for (const wrong of [{ postId: "2" }, { contentVersion: 8 }, { generation: 4 }])
    w.MobileReader.setImages({ image: "data:image/png;base64,YQ==" }, { ...context, ...wrong });
  expect(root.querySelector("img")).toBeNull();
  w.MobileReader.setImages({ image: "file:///token" }, context);
  expect(root.querySelector("img")).toBeNull();
  const images = { image: "data:image/png;base64,YQ==" };
  w.MobileReader.setImages(images, context);
  const image = root.querySelector("img")!;
  expect(root.firstChild).toBe(first);
  expect(w.scrollBy).toHaveBeenLastCalledWith(0, 100 - window.innerHeight * 0.25);
  w.MobileReader.setImages(images, context);
  expect(root.querySelector("img")).toBe(image);
  w.MobileReader.setImages({}, context);
  expect(root.querySelector("img")).toBeNull();
  imageError!(new Event("error"));
  expect(messages.filter((m) => m.payload.message?.startsWith("IMAGE_DECODE_FAILED"))).toHaveLength(0);
  w.MobileReader.setImages(images, context);
  root.querySelector("img")!.dispatchEvent(new Event("error"));
  expect(messages.filter((m) => m.payload.message?.startsWith("IMAGE_DECODE_FAILED"))).toHaveLength(1);
  w.MobileReader.setImages(images, context);
  expect(root.querySelector("img")).toBeNull();
  w.MobileReader.setImages({ image: "data:image/png;base64,Yg==" }, context);
  root.querySelector("img")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  const count = messages.length;
  w.MobileReader.dispose();
  previewError!(new Event("error"));
  w.MobileReader.setImages(images, context);
  expect(messages).toHaveLength(count);
  rects.mockRestore();
});
test("relative authored image sources match the server's canonical absolute manifest URLs without fetching originals", () => {
  const model = JSON.parse(read("scripts/test-fixtures/annotation/rich-document.json"));
  const content = {
    ...model,
    postId: "1",
    contentVersion: 7,
    document: model,
    html: read("scripts/test-fixtures/annotation/rich-page.html") + '<img src="https://[">',
    resources: [{ key: "image", url: "https://staging-bytedepth.bytedepth.cn/image.png" }],
  };
  const { root, messages } = boot("rich", "", { image: "data:image/png;base64,YQ==" }, { content });
  expect(root.querySelector("img")!.getAttribute("src")).toBe("data:image/png;base64,YQ==");
  expect(root.querySelectorAll("img")).toHaveLength(1);
  expect(root.querySelectorAll(".reader-image-missing")).toHaveLength(1);
  expect(messages.filter((m) => m.payload.message === "IMAGE_UNAVAILABLE:unlisted")).toHaveLength(1);
});
test("native table of contents navigation accepts only current model segments and cancels stale correction", () => {
  const { w, root, model, messages } = boot();
  const segment = model.segments[0];
  const element = root.querySelector(`[data-annotation-segment="${segment.id}"]`)!;
  element.scrollIntoView = jest.fn();
  const context = { postId: "1", contentVersion: 7, generation: 3 };
  for (const changed of [{ postId: "2" }, { contentVersion: 8 }, { generation: 4 }])
    w.MobileReader.jumpToSegment(segment.id, { ...context, ...changed });
  w.MobileReader.jumpToSegment("unknown", context);
  expect(element.scrollIntoView).not.toHaveBeenCalled();
  w.MobileReader.jumpToSegment(segment.id, context);
  expect(element.scrollIntoView).toHaveBeenCalledTimes(1);
  expect(messages.at(-1).type).toBe("position");
  element.remove();
  w.MobileReader.jumpToSegment(segment.id, context);
  w.MobileReader.dispose();
  w.MobileReader.jumpToSegment(segment.id, context);
});
test("dark reader selects the actual canonical dark surface and text palette", () => {
  const style = document.createElement("style");
  style.textContent = read(staticRoot + "css/theme.css") + read("apps/mobile/src/reader/web/reader.css");
  document.head.append(style);
  const { w } = boot("rich", "", {}, { theme: "dark" });
  expect(getComputedStyle(document.documentElement).getPropertyValue("--bd-surface").trim()).toBe("#172033");
  expect(getComputedStyle(document.documentElement).getPropertyValue("--bd-text").trim()).toBe("#e5e7eb");
  w.MobileReader.configure({ theme: "light", fontScale: 1 });
  expect(getComputedStyle(document.documentElement).getPropertyValue("--bd-surface").trim()).toBe("#ffffff");
  style.remove();
});
test("an opaque invalid manifest image does not prevent matching a neighboring valid image", () => {
  const model = JSON.parse(read("scripts/test-fixtures/annotation/rich-document.json"));
  const content = {
    ...model,
    postId: "1",
    contentVersion: 7,
    document: model,
    html: read("scripts/test-fixtures/annotation/rich-page.html"),
    resources: [
      { key: "bad", url: "https://[" },
      { key: "image", url: "https://staging-bytedepth.bytedepth.cn/image.png" },
    ],
  };
  const { root } = boot("rich", "", { image: "data:image/png;base64,YQ==" }, { content });
  expect(root.querySelector("img")?.getAttribute("src")).toBe("data:image/png;base64,YQ==");
});
test("reader code follows the real light/dark CSS cascade without Prism shadows or external style changes", () => {
  const runtime = JSON.parse(read("apps/mobile/assets/reader/runtime.json"));
  const style = document.createElement("style");
  style.textContent = [runtime.theme, runtime.prismCss, runtime.codeCss, runtime.css].join("\n");
  document.head.append(style);
  try {
    const { w, root } = boot(
      "rich",
      '<div class="bd-code-block"><div class="bd-code-block__body"><pre><code class="language-java">// comment\nclass Greeter { String s = "hello"; boolean b = true; }</code></pre></div></div><p><code class="language-java">inline</code></p><pre><code class="language-text">plain text</code></pre>',
    );
    w.Prism = { manual: true };
    w.eval(runtime.prism);
    w.eval(runtime.codeBlocks);
    const outside = document.createElement("code");
    outside.className = "language-java";
    outside.textContent = "outside reader";
    document.body.append(outside);
    const keyword = root.querySelector("code .token.keyword")!;
    const comment = root.querySelector("code .token.comment")!;
    const operator = root.querySelector("code .token.operator")!;
    expect(keyword).not.toBeNull();
    expect(comment).not.toBeNull();
    expect(operator).not.toBeNull();
    expect(getComputedStyle(root.querySelector("code.language-text")!).color).toBe("var(--bd-text)");
    for (const theme of ["dark", "light", "dark"]) {
      w.MobileReader.configure({ theme, fontScale: 1 });
      expect(getComputedStyle(document.documentElement).getPropertyValue("--bd-text").trim()).toBe(
        theme === "dark" ? "#e5e7eb" : "#1a1a2e",
      );
      for (const element of root.querySelectorAll("pre,code")) {
        expect(getComputedStyle(element).color).toBe("var(--bd-text)");
        expect(getComputedStyle(element).textShadow).toBe("none");
      }
      for (const element of root.querySelectorAll("pre"))
        expect(getComputedStyle(element).background).toBe("var(--bd-surface-muted)");
      for (const element of root.querySelectorAll("code"))
        expect(getComputedStyle(element).backgroundColor).toBe("rgba(0, 0, 0, 0)");
      for (const element of root.querySelectorAll("code *")) expect(getComputedStyle(element).textShadow).toBe("none");
      if (theme === "dark") {
        expect(getComputedStyle(keyword).color).toBe("var(--bd-info-text)");
        expect(getComputedStyle(comment).color).toBe("var(--bd-text-muted)");
        expect(getComputedStyle(operator).color).toBe("var(--bd-text)");
        for (const token of root.querySelectorAll("code .token"))
          expect(getComputedStyle(token).backgroundColor).toBe("rgba(0, 0, 0, 0)");
      } else {
        expect(getComputedStyle(keyword).color).toBe("rgb(0, 119, 170)");
        expect(getComputedStyle(comment).color).toBe("rgb(112, 128, 144)");
      }
      expect(getComputedStyle(outside).color).toBe("rgb(0, 0, 0)");
      expect(getComputedStyle(outside).textShadow).toBe("0 1px white");
    }
  } finally {
    style.remove();
  }
});
test("layout-generated scrolling preserves the saved character until the reader intentionally scrolls", () => {
  jest.useFakeTimers();
  jest
    .spyOn(Range.prototype, "getClientRects")
    .mockReturnValue([{ top: 200, bottom: 220, left: 22, width: 10 }] as any);
  const { w, root, messages } = boot(
    "rich",
    "",
    {},
    {
      resume: { kind: "exact", target: { segmentId: "s1", codePointOffset: 1, viewportRatio: 0.25 } },
    },
  );
  const node = root.querySelector('[data-annotation-segment="s1"]')!.firstChild!;
  w.document.caretRangeFromPoint = () => {
    const range = document.createRange();
    range.setStart(node, 0);
    range.collapse(true);
    return range;
  };
  window.dispatchEvent(new Event("resize"));
  window.dispatchEvent(new Event("scroll"));
  expect(messages.filter((m) => m.type === "position").at(-1).payload.codePointOffset).toBe(1);
  window.dispatchEvent(new Event("touchstart"));
  window.dispatchEvent(new Event("scroll"));
  jest.advanceTimersByTime(160);
  expect(messages.filter((m) => m.type === "position").at(-1).payload.codePointOffset).toBe(0);
});
