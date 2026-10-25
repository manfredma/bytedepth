import type { ArticleDocument, DocumentSegment, ResumePosition } from "../contracts/mobile";
export interface ResumeTarget {
  segmentId: string;
  codePointOffset: number;
  viewportRatio: number;
}
export type ResumeResolution = { kind: "exact" | "heading"; target: ResumeTarget } | { kind: "start"; target: null };
function blocks(document: ArticleDocument) {
  const grouped = new Map<string, DocumentSegment[]>();
  for (const segment of document.segments) {
    const group = grouped.get(segment.blockId) ?? [];
    group.push(segment);
    grouped.set(segment.blockId, group);
  }
  const points = Array.from(document.text);
  return [...grouped.values()].map((segments) => ({
    segments,
    kind: segments[0].kind,
    start: segments[0].start,
    text: points.slice(segments[0].start, segments[segments.length - 1].end).join(""),
  }));
}
export function resolveResume(
  position: ResumePosition | null | undefined,
  previous: ArticleDocument | null | undefined,
  next: ArticleDocument,
): ResumeResolution {
  const start: ResumeResolution = { kind: "start", target: null };
  if (
    !position ||
    !previous ||
    position.projectionVersion !== previous.projectionVersion ||
    previous.projectionVersion !== next.projectionVersion ||
    !Number.isSafeInteger(position.codePointOffset) ||
    position.codePointOffset < 0 ||
    !Number.isFinite(position.viewportRatio) ||
    position.viewportRatio < 0 ||
    position.viewportRatio > 1
  )
    return start;
  // 工作量有明确上限；旧快照缺失不能作为猜测匹配的依据。
  if (
    previous.segments.length + next.segments.length > 10000 ||
    Array.from(previous.text).length + Array.from(next.text).length > 2000000
  )
    return start;
  const source = previous.segments.find((s) => s.id === position.segmentId);
  const oldBlocks = blocks(previous);
  const sourceBlock = oldBlocks.find((b) => b.segments.includes(source!));
  if (
    !source ||
    !sourceBlock ||
    sourceBlock.text !== position.blockText ||
    sourceBlock.kind !== position.blockKind ||
    position.codePointOffset > Array.from(source.text).length
  )
    return start;
  if (previous.digest === next.digest && previous.text === next.text) {
    const target = next.segments.find(
      (s) => s.id === source.id && s.text === source.text && s.start === source.start && s.kind === source.kind,
    );
    return target
      ? {
          kind: "exact",
          target: {
            segmentId: target.id,
            codePointOffset: position.codePointOffset,
            viewportRatio: position.viewportRatio,
          },
        }
      : start;
  }
  const nextBlocks = blocks(next);
  const matches = (list: ReturnType<typeof blocks>, text: string, kind: string) =>
    list.filter((b) => b.text === text && b.kind === kind);
  const candidates = matches(nextBlocks, sourceBlock.text, sourceBlock.kind);
  if (matches(oldBlocks, sourceBlock.text, sourceBlock.kind).length === 1 && candidates.length === 1) {
    const absolute = candidates[0].start + source.start - sourceBlock.start + position.codePointOffset;
    const target = candidates[0].segments.find((s) => absolute >= s.start && absolute <= s.end);
    if (target)
      return {
        kind: "exact",
        target: {
          segmentId: target.id,
          codePointOffset: absolute - target.start,
          viewportRatio: position.viewportRatio,
        },
      };
  }
  for (const heading of [...position.precedingHeadings].reverse()) {
    const old = matches(oldBlocks, heading, "prose").filter((b) => b.start <= sourceBlock.start);
    const targets = matches(nextBlocks, heading, "prose");
    if (old.length === 1 && matches(oldBlocks, heading, "prose").length === 1 && targets.length === 1)
      return {
        kind: "heading",
        target: { segmentId: targets[0].segments[0].id, codePointOffset: 0, viewportRatio: 0 },
      };
  }
  return start;
}
