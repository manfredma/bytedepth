import { resolveResume } from "../resume";
import type { ArticleDocument, ResumePosition } from "../../contracts/mobile";

function doc(blocks: string[], digest = "a"): ArticleDocument {
  let start = 0;
  return {
    projectionVersion: 1,
    digest,
    text: blocks.join("\n"),
    segments: blocks.map((text, i) => {
      const segment = {
        id: `s${i}`,
        blockId: `b${i}`,
        kind: "prose",
        text,
        start,
        end: start + Array.from(text).length,
      };
      start = segment.end + 1;
      return segment;
    }),
  };
}
const old = doc(["标题", "中文😀正文", "尾声"]);
const position: ResumePosition = {
  postId: "1",
  contentVersion: 1,
  projectionVersion: 1,
  segmentId: "s1",
  codePointOffset: 3,
  blockKind: "prose",
  blockText: "中文😀正文",
  precedingHeadings: ["标题"],
  viewportRatio: 0.3,
  progress: 0.5,
  updatedAt: "2026-10-22T00:00:00Z",
};

test("same document preserves segment-local codepoint and viewport anchor", () => {
  expect(resolveResume(position, old, old)).toEqual({
    kind: "exact",
    target: { segmentId: "s1", codePointOffset: 3, viewportRatio: 0.3 },
  });
});
test("cross revision requires unchanged full blocks unique in both versions", () => {
  expect(resolveResume(position, old, doc(["前言", "标题", "中文😀正文"], "b"))).toMatchObject({
    kind: "exact",
    target: { segmentId: "s2", codePointOffset: 3 },
  });
  expect(resolveResume(position, doc(["标题", "中文😀正文", "中文😀正文"]), doc(["中文😀正文"], "b"))).toEqual({
    kind: "start",
    target: null,
  });
  expect(resolveResume(position, old, doc(["标题", "中文😀正文", "中文😀正文"], "b")).kind).toBe("heading");
});
test("changed or missing blocks fall back to unique latest preceding heading then start", () => {
  expect(resolveResume(position, old, doc(["标题", "改写正文"], "b"))).toEqual({
    kind: "heading",
    target: { segmentId: "s0", codePointOffset: 0, viewportRatio: 0 },
  });
  expect(resolveResume(position, old, doc(["标题", "标题"], "b")).kind).toBe("start");
  expect(resolveResume(position, null, old).kind).toBe("start");
  expect(resolveResume(null, old, old).kind).toBe("start");
  expect(resolveResume({ ...position, codePointOffset: 999 }, old, old).kind).toBe("start");
  expect(resolveResume({ ...position, blockText: "伪造" }, old, old).kind).toBe("start");
});
test("bounded resolver refuses excessive snapshots and same-digest changed segment identities", () => {
  expect(resolveResume(position, { ...old, text: "x".repeat(2000001) }, old).kind).toBe("start");
  expect(resolveResume(position, { ...old, segments: Array(10001).fill(old.segments[0]) }, old).kind).toBe("start");
  expect(
    resolveResume(position, old, { ...old, segments: old.segments.map((s) => ({ ...s, id: `new-${s.id}` })) }).kind,
  ).toBe("start");
  const broken = doc(["中文😀正文"], "b");
  broken.segments[0].start = 100;
  broken.segments[0].end = 100;
  expect(resolveResume(position, old, broken).kind).toBe("start");
});
test("a cross-version offset landing in a new virtual separator gap falls back rather than guessing a segment", () => {
  const previous = doc(["a\n\nb"]);
  const next = {
    ...doc(["a\n\nb"], "b"),
    segments: [
      { id: "s0", blockId: "b0", kind: "prose", text: "a", start: 0, end: 1 },
      { id: "s1", blockId: "b0", kind: "prose", text: "b", start: 3, end: 4 },
    ],
  };
  expect(
    resolveResume(
      { ...position, segmentId: "s0", codePointOffset: 2, blockText: "a\n\nb", precedingHeadings: [] },
      previous,
      next,
    ).kind,
  ).toBe("start");
});
