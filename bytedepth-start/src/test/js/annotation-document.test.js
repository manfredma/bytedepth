import fs from "node:fs";
import path from "node:path";
import { JSDOM } from "jsdom";
import { afterEach, describe, expect, test } from "vitest";
import "../../main/resources/static/js/annotation-document.js";
const mappingAPI = window.AnnotationDocument;

const base = "bytedepth-start/src/main/resources/static/";
const fixtureRoot = "scripts/test-fixtures/annotation/";
const windows = [];
const read = (name) => fs.readFileSync(name, "utf8");
function boot(name = "rich", modify = (model) => model) {
  const model = modify({ ...JSON.parse(read(`${fixtureRoot}${name}-document.json`)), revision: 7 });
  const dom = new JSDOM(
    `<article id="post-article"><div class="content">${read(`${fixtureRoot}${name}-page.html`)}</div></article>`,
    {
      runScripts: "outside-only",
    },
  );
  windows.push(dom.window);
  for (const script of ["vendor/hypothesis-anchoring/trim-range.js", "vendor/hypothesis-anchoring/text-range.js"]) {
    if (fs.existsSync(base + script))
      dom.window.eval(read(base + script) + "\n//# sourceURL=" + path.resolve(base + script));
  }
  dom.window.AnnotationDocument = mappingAPI;
  expect(dom.window.AnnotationDocument, "versioned pure-content mapping API").toBeDefined();
  const root = dom.window.document.querySelector(".content");
  return { w: dom.window, root, model, map: dom.window.AnnotationDocument.create(root, model) };
}
function range(w, node, start, end) {
  const r = w.document.createRange();
  r.setStart(node, start);
  r.setEnd(node, end);
  return r;
}
function view(model, exact, spans, overrides = {}) {
  return {
    anchor: { exact },
    targetDigest: model.digest,
    placement: { status: "ANCHORED", targetRevision: 7, projectionVersion: 1, spans },
    ...overrides,
  };
}
afterEach(() => {
  for (const w of windows.splice(0)) w.close();
});

describe("canonical annotation DOM mapping", () => {
  for (const name of ["rich", "unicode", "duplicates", "footnotes"]) {
    test(`U28_${name}_everyActualSanitizedSegmentMatchesTheReviewedModel`, () => {
      const { w, root, model, map } = boot(name);
      expect([...root.querySelectorAll("[data-annotation-segment]")].map((e) => e.textContent)).toEqual(
        model.segments.map((s) => s.text),
      );
      for (const segment of model.segments.filter((s) => s.start !== s.end)) {
        const spans = [{ segmentId: segment.id, start: 0, end: [...segment.text].length }];
        const ranges = map.verify(view(model, segment.text, spans));
        expect(Array.isArray(ranges)).toBe(true);
        expect(ranges.map((r) => r.toString()).join("")).toBe(segment.text);
        const result = map.describe(ranges[0]);
        expect(result.exact).toBe(segment.text);
        expect(result.start).toBe(segment.start);
        expect(result.end).toBe(segment.end);
      }
      expect(w.document.querySelector("img")?.textContent ?? "").toBe("");
    });
  }

  test("U01_U10_crossBlockAndInlineSelectionsUseSeparatePureRanges", () => {
    const { w, root, model, map } = boot();
    const first = root.querySelector('[data-annotation-segment="s1"]');
    const last = root.querySelector('[data-annotation-segment="s7"]');
    const r = w.document.createRange();
    r.setStart(first.firstChild, 0);
    r.setEnd(last.firstChild, 1);
    const selected = map.describe(r);
    expect(selected.exact).toBe("前文重点与inline。\nint anchorProbe = 1;\nA");
    expect(selected.spans.map((s) => s.segmentId)).toEqual(["s1", "s2", "s3", "s4", "s5", "s6", "s7"]);
    const ranges = map.verify(view(model, selected.exact, selected.spans));
    expect(ranges.map((r) => r.toString())).toEqual([
      "前文",
      "重点",
      "与",
      "inline",
      "。",
      "int anchorProbe = 1;\n",
      "A",
    ]);
    expect(ranges.some((r) => r.toString().includes("复制"))).toBe(false);
  });

  test("U13_U14_copyLabelsLanguageAndLineNumbersCannotShiftCodeCoordinates", () => {
    const { w, root, map } = boot();
    const code = root.querySelector("pre code");
    const selected = () => map.describe(range(w, code.firstChild, 4, 15));
    expect(selected()).toMatchObject({ exact: "anchorProbe", start: 20, end: 31 });
    root.querySelector(".bd-code-block__copy").textContent = "已复制/复制失败";
    root.querySelector(".bd-code-block__language").textContent = "long-language-name";
    root.querySelector(".bd-code-block__line").textContent = "10000";
    expect(selected()).toMatchObject({ exact: "anchorProbe", start: 20, end: 31 });
  });

  test("U15_U16_actualPrismAndCodeBlocksKeepBidirectionalMapping", () => {
    const { w, root, model, map } = boot();
    w.Prism = { manual: true };
    w.eval(read(base + "vendor/prism/prism-common.min.js"));
    w.eval(read(base + "js/code-blocks.js"));
    w.document.dispatchEvent(new w.Event("DOMContentLoaded"));
    w.Prism.highlightAllUnder(root);
    for (let n = 0; n < 2; n++) {
      const spans = [{ segmentId: "s6", start: 4, end: 15 }];
      const ranges = map.verify(view(model, "anchorProbe", spans));
      expect(ranges[0].toString()).toBe("anchorProbe");
      expect(map.describe(ranges[0])).toMatchObject({ exact: "anchorProbe", start: 20, end: 31 });
      w.Prism.highlightAllUnder(root);
    }
  });

  test("U24_U25_surrogateBoundariesNfdZwjAndNbspAreExact", () => {
    const { w, root, map } = boot("unicode");
    const node = root.querySelector("[data-annotation-segment]").firstChild;
    expect(map.describe(range(w, node, 1, 3))).toMatchObject({ exact: "😀", start: 1, end: 2 });
    expect(map.describe(range(w, node, 2, 3))).toHaveProperty("error");
    expect(map.describe(range(w, node, 3, 5))).toMatchObject({ exact: "e\u0301", start: 2, end: 4 });
    expect(map.describe(range(w, node, 5, 6))).toMatchObject({ exact: "é", start: 4, end: 5 });
  });

  test("U09_U12_invalidRangesModelsAndUnknownRulesFailClosed", () => {
    const { w, root, model, map } = boot();
    for (const spans of [
      [],
      [{ segmentId: "missing", start: 0, end: 1 }],
      [{ segmentId: "s1", start: 2, end: 1 }],
      [{ segmentId: "s1", start: -1, end: 1 }],
      [{ segmentId: "s1", start: 0, end: 100 }],
    ]) {
      expect(map.ranges(spans)).toHaveProperty("error");
    }
    expect(map.verify(view(model, "错误文字", [{ segmentId: "s1", start: 0, end: 2 }])).error).toBe("INVALID_MAPPING");
    expect(
      map.verify(view(model, "前文", [{ segmentId: "s1", start: 0, end: 2 }], { targetDigest: "0".repeat(64) })),
    ).toHaveProperty("error");
    expect(
      map.describe(range(w, root.querySelector('[data-annotation-segment="s1"]').firstChild, 0, 0)),
    ).toHaveProperty("error");
    const invalid = w.AnnotationDocument.create(root, { ...model, projectionVersion: 99 });
    expect(invalid.describe(w.getSelection())).toHaveProperty("error");
  });

  test("U23_U30_unknownReplacementDetachedRootsAndDisposeDoNotPaintPartialRanges", () => {
    const { root, model, map } = boot();
    const spans = [{ segmentId: "s1", start: 0, end: 2 }];
    const original = root.querySelector('[data-annotation-segment="s1"]');
    original.innerHTML = "<svg><text>前文</text></svg>";
    expect(map.verify(view(model, "前文", spans))).toHaveProperty("error");
    original.textContent = "前文";
    expect(map.verify(view(model, "前文", spans))[0].toString()).toBe("前文");
    root.remove();
    expect(map.ranges(spans)).toHaveProperty("error");
    map.dispose();
    map.dispose();
    expect(map.describe(null)).toHaveProperty("error");
  });
});

test("U09_U12_corruptedProjectionShapesNeverBecomeUsableMappings", () => {
  const { w, root, model } = boot();
  const cases = [
    (m) => {
      m.revision = -1;
    },
    (m) => {
      m.revision = 0;
    },
    (m) => {
      m.digest = "wrong";
    },
    (m) => {
      m.text = null;
    },
    (m) => {
      m.segments = null;
    },
    (m) => {
      m.segments[0].id = "bad";
    },
    (m) => {
      m.segments[1].id = "s0";
    },
    (m) => {
      m.segments[0].blockId = "bad";
    },
    (m) => {
      m.segments[0].kind = "svg";
    },
    (m) => {
      m.segments[0].text = null;
    },
    (m) => {
      m.segments[0].start = 0.5;
    },
    (m) => {
      m.segments[0].end = -1;
    },
    (m) => {
      m.segments[1].start = 0;
    },
    (m) => {
      m.segments[1].end = 0;
    },
    (m) => {
      m.segments[0].end = 1;
    },
    (m) => {
      m.segments[0].text = "错误";
    },
    (m) => {
      m.text = m.text.replace("\n", "X");
    },
    (m) => {
      m.text += "尾巴";
    },
    (m) => {
      m.text = "\n" + m.text;
      for (const s of m.segments) {
        s.start++;
        s.end++;
      }
    },
  ];
  for (const modify of cases) {
    const bad = structuredClone(model);
    modify(bad);
    expect(w.AnnotationDocument.create(root, bad).ranges([{ segmentId: "s0", start: 0, end: 1 }])).toHaveProperty(
      "error",
    );
  }
  expect(w.AnnotationDocument.create(root, undefined).ranges([])).toHaveProperty("error");
  expect(w.AnnotationDocument.create(null, model).ranges([])).toHaveProperty("error");
  root.dataset.annotationRevision = "8";
  expect(w.AnnotationDocument.create(root, model).ranges([{ segmentId: "s0", start: 0, end: 1 }])).toHaveProperty(
    "error",
  );
  root.dataset.annotationRevision = "7";
  expect(
    w.AnnotationDocument.create(root, model)
      .ranges([{ segmentId: "s0", start: 0, end: 1 }])[0]
      .toString(),
  ).toBe("阅");
});

test("U09_U10_incompleteReorderedOrNoncontiguousSpansAreRejected", () => {
  const { map, model } = boot();
  for (const spans of [
    null,
    [{ segmentId: "s1", start: 0.1, end: 1 }],
    [{ segmentId: "s1", start: 0, end: null }],
    [
      { segmentId: "s1", start: 0, end: 1 },
      { segmentId: "s2", start: 0, end: 1 },
    ],
    [
      { segmentId: "s1", start: 0, end: 2 },
      { segmentId: "s2", start: 1, end: 2 },
    ],
    [
      { segmentId: "s1", start: 0, end: 2 },
      { segmentId: "s3", start: 0, end: 1 },
    ],
    [
      { segmentId: "s1", start: 0, end: 2 },
      { segmentId: "s1", start: 0, end: 1 },
    ],
  ])
    expect(map.ranges(spans)).toHaveProperty("error");
  const good = view(model, "前文", [{ segmentId: "s1", start: 0, end: 2 }]);
  for (const bad of [
    null,
    {},
    { ...good, anchor: undefined },
    { ...good, anchor: { exact: "  " } },
    { ...good, placement: { ...good.placement, status: "UNRESOLVED" } },
    { ...good, placement: { ...good.placement, targetRevision: 6 } },
    { ...good, placement: { ...good.placement, projectionVersion: 2 } },
  ])
    expect(map.verify(bad)).toHaveProperty("error");
});

test("U09_U23_U30_missingDuplicatedAndChangedRootsCannotBeMapped", () => {
  const { w, root, model, map } = boot();
  const spans = [{ segmentId: "s1", start: 0, end: 2 }];
  const el = root.querySelector('[data-annotation-segment="s1"]');
  el.remove();
  expect(map.ranges(spans)).toHaveProperty("error");
  root.prepend(el);
  const duplicate = el.cloneNode(true);
  root.append(duplicate);
  expect(map.ranges(spans)).toHaveProperty("error");
  duplicate.remove();
  el.textContent = "错字";
  expect(map.ranges(spans)).toHaveProperty("error");
  el.textContent = "前文";
  const fake = w.document.createElement("div");
  fake.dataset.annotationSegment = "s1";
  fake.textContent = "前文";
  el.replaceWith(fake);
  expect(map.ranges(spans)).toHaveProperty("error");
  fake.replaceWith(el);
  const mermaid = structuredClone(model);
  mermaid.segments[1].kind = "mermaid";
  expect(w.AnnotationDocument.create(root, mermaid).ranges(spans)).toHaveProperty("error");
  const outside = w.document.createTextNode("外部");
  w.document.body.append(outside);
  expect(map.describe(range(w, outside, 0, 1))).toHaveProperty("error");
  expect(map.describe(w.getSelection())).toHaveProperty("error");
  expect(map.describe({ rangeCount: 2 })).toHaveProperty("error");
  const selection = w.getSelection();
  selection.addRange(range(w, el.firstChild, 0, 2));
  expect(map.describe(selection).exact).toBe("前文");
});

test("U30_reorderedContentRootsMustNotSilentlyRewriteSelectionOrder", () => {
  const { w, root, map } = boot();
  const first = root.querySelector('[data-annotation-segment="s1"]');
  const second = root.querySelector('[data-annotation-segment="s2"]');
  second.before(first);
  first.before(second);
  const r = w.document.createRange();
  r.setStart(second.firstChild, 0);
  r.setEnd(first.firstChild, 2);
  expect(map.describe(r)).toHaveProperty("error");
});

test("U24_contextIs128CodepointsAndWhitespaceOnlySelectionIsRejected", () => {
  const { w, root, map } = boot();
  const code = root.querySelector("pre code");
  expect(map.describe(range(w, code.firstChild, 3, 4))).toHaveProperty("error");
  const text = "😀".repeat(130) + "目标" + "e\u0301".repeat(130);
  root.replaceChildren();
  const span = w.document.createElement("span");
  span.dataset.annotationSegment = "s0";
  span.textContent = text;
  root.append(span);
  const m = {
    revision: 1,
    projectionVersion: 1,
    digest: "a".repeat(64),
    text,
    segments: [{ id: "s0", blockId: "b0", kind: "prose", text, start: 0, end: 392 }],
  };
  const mapped = w.AnnotationDocument.create(root, m).describe(range(w, span.firstChild, 260, 262));
  expect(mapped).toMatchObject({
    exact: "目标",
    start: 130,
    end: 132,
    prefix: "😀".repeat(128),
    suffix: "e\u0301".repeat(64),
  });
});

test("U09_emptyCodeRootsContributeNoSelectableText", () => {
  const { w, root, model } = boot();
  const empty = w.document.createElement("span");
  empty.dataset.annotationSegment = "s99";
  root.prepend(empty);
  const m = structuredClone(model);
  m.segments.unshift({ id: "s99", blockId: "b99", kind: "code", text: "", start: 0, end: 0 });
  const r = w.document.createRange();
  r.selectNodeContents(root);
  const result = w.AnnotationDocument.create(root, m).describe(r);
  expect(result.exact).toBe(model.text);
  expect(result.spans.some((span) => span.segmentId === "s99")).toBe(false);
});

test("U30_dependencyReturningAWrongDomRangeIsRejectedBeforeDisplay", () => {
  const { w, root, map } = boot();
  const wrong = w.document.createRange();
  wrong.selectNodeContents(root.querySelector('[data-annotation-segment="s1"]'));
  // Fault injection at the external helper boundary; all normal mapping tests execute the real helper.
  w.HypothesisTextRange = { TextRange: { fromOffsets: () => ({ toRange: () => wrong }) } };
  expect(map.ranges([{ segmentId: "s1", start: 0, end: 1 }])).toHaveProperty("error");
});

test("U09_U26_optionalSelectionTrimPreservesCanonicalWhitespaceOutsideTheQuote", () => {
  const { w, root, model, map } = boot();
  const segment = model.segments.at(-1);
  const el = root.querySelector(`[data-annotation-segment="${segment.id}"]`);
  const r = w.document.createRange();
  r.selectNodeContents(el);
  const raw = map.describe(r);
  const trimmed = map.describe(r, { trim: true });
  expect(raw.exact).toBe(" & \u00a0");
  expect(trimmed.exact).toBe("&");
  expect(trimmed.suffix).toBe(" \u00a0");
  expect(trimmed.end).toBe(raw.end - 2);
  expect(trimmed.start).toBe(raw.start + 1);
  expect(trimmed.prefix.endsWith("后文  ")).toBe(true);
  root.innerHTML = '<span data-annotation-segment="s0">   </span>';
  const spaces = mapperForSpaces(root);
  const blank = w.document.createRange();
  blank.selectNodeContents(root.firstChild);
  expect(spaces.describe(blank, { trim: true })).toEqual({ error: "INVALID_MAPPING" });
});
function mapperForSpaces(root) {
  return mappingAPI.create(root, {
    revision: 7,
    projectionVersion: 1,
    digest: "d".repeat(64),
    text: "   ",
    segments: [{ id: "s0", blockId: "b0", kind: "prose", text: "   ", start: 0, end: 3 }],
  });
}
