import fs from "node:fs";
import { JSDOM } from "jsdom";
import { afterEach, expect, test, vi } from "vitest";
import "../../main/resources/static/js/annotation-document.js";
import "../../main/resources/static/js/annotation-display.js";
const mapper = window.AnnotationDocument;
const instances = [];
const displayAPI = window.AnnotationDisplay;
function boot() {
  const model = {
    ...JSON.parse(fs.readFileSync("scripts/test-fixtures/annotation/rich-document.json", "utf8")),
    revision: 7,
  };
  const dom = new JSDOM(
    `<article id="post-article"><div class="content">${fs.readFileSync("scripts/test-fixtures/annotation/rich-page.html", "utf8")}</div></article>`,
    { runScripts: "outside-only" },
  );
  instances.push(dom.window);
  for (const f of ["trim-range", "text-range"])
    dom.window.eval(
      fs.readFileSync(`bytedepth-start/src/main/resources/static/vendor/hypothesis-anchoring/${f}.js`, "utf8"),
    );
  const root = dom.window.document.querySelector(".content");
  dom.window.Range.prototype.getClientRects = () => [
    { left: 10, right: 90, top: 20, bottom: 40, width: 80, height: 20 },
  ];
  dom.window.Range.prototype.getBoundingClientRect = () => ({
    left: 10,
    right: 90,
    top: 20,
    bottom: 40,
    width: 80,
    height: 20,
  });
  expect(displayAPI, "verified-range display adapter").toBeDefined();
  return {
    w: dom.window,
    root,
    model,
    map: mapper.create(root, model),
    display: displayAPI.create(root, mapper.create(root, model)),
  };
}
function view(model, id, exact, spans, extra = {}) {
  return {
    id,
    color: "yellow",
    annotationText: null,
    anchor: { exact },
    targetDigest: model.digest,
    placement: { status: "ANCHORED", targetRevision: 7, projectionVersion: 1, spans },
    ...extra,
  };
}
afterEach(() => {
  for (const w of instances.splice(0)) w.close();
});

test("U01_U10_overlappingAndCrossBlockViewsPaintOnlyVerifiedPureText", () => {
  const { root, model, display } = boot();
  const a = view(model, 1, "重点", [{ segmentId: "s2", start: 0, end: 2 }]);
  const b = view(model, 2, "点", [{ segmentId: "s2", start: 1, end: 2 }], { color: "red" });
  const c = view(model, 3, "。\nint anchorProbe = 1;", [
    { segmentId: "s5", start: 0, end: 1 },
    { segmentId: "s6", start: 0, end: 20 },
  ]);
  const original = root.textContent;
  expect([...display.render([a, b, c])]).toEqual([1, 2, 3]);
  expect([...root.querySelectorAll('mark[data-id="1"]')].map((m) => m.textContent).join("")).toBe("重点");
  expect(root.querySelector('mark[data-id="2"]').textContent).toBe("点");
  expect([...root.querySelectorAll('mark[data-id="3"]')].map((m) => m.textContent).join("")).toBe(
    "。int anchorProbe = 1;",
  );
  expect(root.textContent).toBe(original);
  display.render([a, b, c]);
  expect(root.querySelectorAll('mark[data-id="2"]')).toHaveLength(1);
  display.clear();
  expect(root.querySelector("mark")).toBeNull();
  expect(root.textContent).toBe(original);
});

test("U09_U11_mismatchedQuotesRevisionsAndIncompleteMappingsNeverPaintPartialRanges", () => {
  const { root, model, display } = boot();
  const good = view(model, 1, "重点", [{ segmentId: "s2", start: 0, end: 2 }]);
  const forged = { ...good, id: 2, anchor: { exact: "错字" } };
  const stale = { ...good, id: 3, placement: { ...good.placement, targetRevision: 6 } };
  const missing = {
    ...good,
    id: 4,
    placement: { ...good.placement, spans: [...good.placement.spans, { segmentId: "s999", start: 0, end: 1 }] },
  };
  const unresolved = { ...good, id: 5, placement: { ...good.placement, status: "UNRESOLVED", spans: [] } };
  expect([...display.render([forged, stale, missing, unresolved])]).toEqual([]);
  expect(root.querySelector("mark")).toBeNull();
  expect(forged.anchor.exact).toBe("错字");
  expect([...display.render([good])]).toEqual([1]);
  root.querySelector('[data-annotation-segment="s2"]').textContent = "变更";
  display.relayout();
  expect(root.querySelector("mark")).toBeNull();
});

test("U29_U31_commentGeometryRelayoutAndFocusAreSeparateFromAnchorIdentity", () => {
  const { w, root, model, display } = boot();
  root.getBoundingClientRect = () => ({ left: 2, top: 5 });
  const a = view(model, 1, "重点", [{ segmentId: "s2", start: 0, end: 2 }], { annotationText: "评论" });
  display.render([a]);
  const outline = root.querySelector(".bd-annotation-comment-outline");
  expect(outline.style.left).toBe("8px");
  expect(outline.style.top).toBe("6px");
  let focused;
  root.addEventListener("bd:annotation-focus", (e) => (focused = e.detail));
  outline.querySelector("button").click();
  expect(focused).toBe(1);
  w.Range.prototype.getClientRects = () => [{ left: 15, right: 115, top: 40, bottom: 60, width: 100, height: 20 }];
  display.relayout(1);
  expect(root.querySelector(".bd-annotation-comment-outline").style.top).toBe("26px");
  expect(root.querySelector(".bd-annotation-comment-outline-active")).not.toBeNull();
  expect(root.querySelector("mark").textContent).toBe("重点");
  display.dispose();
  display.dispose();
  expect(root.querySelector("mark")).toBeNull();
  expect([...display.render([a])]).toEqual([]);
});

test("U09_U30_duplicateIdentityBadColorsAndDetachedRootsFailClosed", () => {
  const { w, root, model, display } = boot();
  const good = view(model, 1, "重点", [{ segmentId: "s2", start: 0, end: 2 }]);
  expect([
    ...display.render([{ ...good, id: 0 }, { ...good, id: "1" }, { ...good, id: 2, color: "evil" }, good, { ...good }]),
  ]).toEqual([]);
  display.render([good]);
  root.remove();
  display.relayout();
  expect(root.querySelector("mark")).toBeNull();
  expect([...display.render([good])]).toEqual([]);
  w.document.body.append(root);
  const other = view(model, 2, "前文", [{ segmentId: "s1", start: 0, end: 2 }]);
  display.render([good, other]);
  root.querySelector('[data-annotation-segment="s2"]').append("变");
  display.relayout();
  expect(root.querySelector('mark[data-id="1"]')).toBeNull();
  expect(root.querySelector('mark[data-id="2"]').textContent).toBe("前文");
});

test("U15_U16_realPrismTokenSplitsAndRepeatedPaintKeepCodeAndCopyTextExact", async () => {
  const { w, root, model, display } = boot();
  const original = root.querySelector("pre code").textContent;
  for (const file of ["vendor/prism/prism-common.min.js", "js/code-blocks.js"])
    w.eval(fs.readFileSync(`bytedepth-start/src/main/resources/static/${file}`, "utf8"));
  w.document.dispatchEvent(new w.Event("DOMContentLoaded"));
  w.Prism.highlightAllUnder(root);
  const a = view(model, 1, "int anchorProbe = 1;", [{ segmentId: "s6", start: 0, end: 20 }]);
  for (let i = 0; i < 2; i++) {
    display.render([a]);
    expect([...root.querySelectorAll('mark[data-id="1"]')].map((m) => m.textContent).join("")).toBe(
      "int anchorProbe = 1;",
    );
    expect(root.querySelector("pre code").textContent).toBe(original);
    w.Prism.highlightAllUnder(root);
  }
  const copied = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(w.navigator, "clipboard", { configurable: true, value: { writeText: copied } });
  root.querySelector(".bd-code-block__copy").click();
  await Promise.resolve();
  expect(copied).toHaveBeenCalledWith("int anchorProbe = 1;\n");
});

test("U31_realRangeRectsMergeOnlyMatchingRowsAndUseMeasuredFallback", () => {
  const { w, root, model, display } = boot();
  const rect = (left, top, right, bottom) => ({ left, top, right, bottom, width: right - left, height: bottom - top });
  w.Range.prototype.getClientRects = () => [
    rect(30, 20, 50, 40),
    rect(10, 20, 20, 40),
    rect(60, 20, 90, 45),
    rect(0, 80, 40, 100),
    rect(0, 0, 0, 20),
    rect(0, 0, 20, 0),
  ];
  const a = view(model, 1, "重点", [{ segmentId: "s2", start: 0, end: 2 }], { annotationText: "评论" });
  display.render([a]);
  let outlines = [...root.querySelectorAll(".bd-annotation-comment-outline")];
  expect(outlines).toHaveLength(3);
  expect(outlines[0].style.width).toBe("40px");
  expect(outlines[1].style.top).toBe("20px");
  expect(outlines[2].style.top).toBe("80px");
  w.Range.prototype.getClientRects = () => [];
  w.Range.prototype.getBoundingClientRect = () => rect(0, 5, 10, 15);
  display.relayout();
  expect(root.querySelector(".bd-annotation-comment-outline").style.width).toBe("10px");
  for (const fallback of [rect(0, 0, 0, 10), rect(0, 0, 10, 0)]) {
    w.Range.prototype.getBoundingClientRect = () => fallback;
    display.relayout();
    expect(root.querySelector(".bd-annotation-comment-outline")).toBeNull();
  }
});
