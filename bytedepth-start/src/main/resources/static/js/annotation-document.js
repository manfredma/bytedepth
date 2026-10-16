(function () {
  "use strict";

  function create(root, input) {
    const window = root?.ownerDocument?.defaultView;
    let model;
    let segments;
    let disposed = false;
    let valid = true;
    const reject = () => {
      throw new Error("INVALID_MAPPING");
    };
    const codepoints = (text) => Array.from(text);
    const integer = (value) => Number.isSafeInteger(value) && value >= 0;
    try {
      model = JSON.parse(JSON.stringify(input));
      if (
        model.projectionVersion !== 1 ||
        !integer(model.revision) ||
        model.revision < 1 ||
        !/^[0-9a-f]{64}$/.test(model.digest) ||
        typeof model.text !== "string" ||
        !Array.isArray(model.segments)
      ) {
        reject();
      }
      segments = new Map();
      let end = 0;
      const text = codepoints(model.text);
      for (const segment of model.segments) {
        if (
          !/^s[0-9]+$/.test(segment.id) ||
          segments.has(segment.id) ||
          !/^b[0-9]+$/.test(segment.blockId) ||
          !["prose", "code", "mermaid"].includes(segment.kind) ||
          typeof segment.text !== "string" ||
          !integer(segment.start) ||
          !integer(segment.end) ||
          segment.start < end ||
          segment.end < segment.start ||
          segment.end - segment.start !== codepoints(segment.text).length ||
          text.slice(segment.start, segment.end).join("") !== segment.text ||
          !/^[\n\t]*$/.test(text.slice(end, segment.start).join(""))
        ) {
          reject();
        }
        segments.set(segment.id, { ...segment, index: segments.size });
        end = segment.end;
      }
      if (end !== text.length || (segments.size && model.segments[0].start !== 0)) {
        reject();
      }
    } catch {
      valid = false;
    }

    function guard(action) {
      try {
        if (
          !valid ||
          disposed ||
          !root?.isConnected ||
          (root.dataset.annotationRevision && Number(root.dataset.annotationRevision) !== model.revision)
        ) {
          reject();
        }
        return action();
      } catch {
        return { error: "INVALID_MAPPING" };
      }
    }

    function element(segment) {
      const matches = root.querySelectorAll(`[data-annotation-segment="${segment.id}"]`);
      if (matches.length !== 1 || segment.kind === "mermaid") {
        reject();
      }
      const el = matches[0];
      if (
        !["SPAN", "CODE"].includes(el.tagName) ||
        el.textContent !== segment.text ||
        el.querySelector("button,svg,math,script,style,iframe,object,img,[data-annotation-segment]")
      ) {
        reject();
      }
      return el;
    }

    function utf16ToCodepoint(text, offset) {
      if (
        !integer(offset) ||
        offset > text.length ||
        (offset > 0 &&
          offset < text.length &&
          /[\uD800-\uDBFF]/.test(text[offset - 1]) &&
          /[\uDC00-\uDFFF]/.test(text[offset]))
      ) {
        reject();
      }
      return codepoints(text.slice(0, offset)).length;
    }

    function resolve(spans) {
      if (!Array.isArray(spans) || !spans.length) {
        reject();
      }
      const result = [];
      let previous;
      for (const span of spans) {
        const segment = segments.get(span.segmentId);
        if (
          !segment ||
          !integer(span.start) ||
          !integer(span.end) ||
          span.end <= span.start ||
          span.end > segment.end - segment.start
        ) {
          reject();
        }
        if (previous) {
          if (
            segment.index <= previous.segment.index ||
            previous.span.end !== codepoints(previous.segment.text).length ||
            span.start !== 0 ||
            model.segments.slice(previous.segment.index + 1, segment.index).some((s) => s.text.length)
          ) {
            reject();
          }
        }
        const el = element(segment);
        if (previous && !(previous.el.compareDocumentPosition(el) & window.Node.DOCUMENT_POSITION_FOLLOWING)) {
          reject();
        }
        const text = codepoints(segment.text);
        const range = window.HypothesisTextRange.TextRange.fromOffsets(
          el,
          text.slice(0, span.start).join("").length,
          text.slice(0, span.end).join("").length,
        ).toRange();
        if (range.toString() !== text.slice(span.start, span.end).join("")) {
          reject();
        }
        result.push(range);
        previous = { span, segment, el };
      }
      return result;
    }

    function bounds(spans) {
      const first = spans[0];
      const last = spans[spans.length - 1];
      return {
        start: segments.get(first.segmentId).start + first.start,
        end: segments.get(last.segmentId).start + last.end,
      };
    }

    function describe(selection) {
      return guard(() => {
        let range = selection;
        if (selection && "rangeCount" in selection) {
          if (selection.rangeCount !== 1) {
            reject();
          }
          range = selection.getRangeAt(0);
        }
        if (!range || range.collapsed || !root.contains(range.startContainer) || !root.contains(range.endContainer)) {
          reject();
        }
        const spans = [];
        for (const segment of model.segments) {
          const els = root.querySelectorAll(`[data-annotation-segment="${segment.id}"]`);
          if (![...els].some((el) => range.intersectsNode(el))) {
            continue;
          }
          const el = element(segment);
          const full = root.ownerDocument.createRange();
          full.selectNodeContents(el);
          const clipped = range.cloneRange();
          if (clipped.compareBoundaryPoints(window.Range.START_TO_START, full) < 0) {
            clipped.setStart(full.startContainer, full.startOffset);
          }
          if (clipped.compareBoundaryPoints(window.Range.END_TO_END, full) > 0) {
            clipped.setEnd(full.endContainer, full.endOffset);
          }
          if (clipped.collapsed) {
            continue;
          }
          const relative = window.HypothesisTextRange.TextRange.fromRange(clipped).relativeTo(el);
          spans.push({
            segmentId: segment.id,
            start: utf16ToCodepoint(segment.text, relative.start.offset),
            end: utf16ToCodepoint(segment.text, relative.end.offset),
          });
        }
        resolve(spans);
        const { start, end } = bounds(spans);
        const text = codepoints(model.text);
        const exact = text.slice(start, end).join("");
        if (!exact.trim()) {
          reject();
        }
        return {
          exact,
          prefix: text.slice(Math.max(0, start - 128), start).join(""),
          suffix: text.slice(end, end + 128).join(""),
          start,
          end,
          spans,
        };
      });
    }

    function verify(view) {
      return guard(() => {
        const placement = view?.placement;
        if (
          !placement ||
          placement.status !== "ANCHORED" ||
          placement.targetRevision !== model.revision ||
          placement.projectionVersion !== model.projectionVersion ||
          view.targetDigest !== model.digest ||
          typeof view.anchor?.exact !== "string"
        ) {
          reject();
        }
        const ranges = resolve(placement.spans);
        const { start, end } = bounds(placement.spans);
        if (codepoints(model.text).slice(start, end).join("") !== view.anchor.exact || !view.anchor.exact.trim()) {
          reject();
        }
        return ranges;
      });
    }

    return {
      describe,
      ranges: (spans) => guard(() => resolve(spans)),
      verify,
      dispose: () => {
        disposed = true;
      },
    };
  }

  window.AnnotationDocument = Object.freeze({ create });
})();
