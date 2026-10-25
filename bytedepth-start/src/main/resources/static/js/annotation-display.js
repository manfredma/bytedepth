/* 仅绘制已根据当前纯内容 DOM 独立核验的服务端定位。 */
(function () {
  "use strict";
  function create(root, documentMap) {
    const w = root.ownerDocument.defaultView;
    const doc = root.ownerDocument;
    const colors = new Set(["yellow", "red", "green", "blue"]);
    let views = [];
    let disposed = false;
    let layer;
    const hasComment = (view) => Boolean(view.annotationText?.trim());

    function unwrap(id) {
      root.querySelectorAll("mark.bd-annotation-highlight").forEach((mark) => {
        if (id !== undefined && mark.dataset.id !== String(id)) {
          return;
        }
        const parent = mark.parentNode;
        mark.replaceWith(...mark.childNodes);
        parent.normalize();
      });
    }

    function clear() {
      layer?.remove();
      layer = null;
      unwrap();
      views = [];
    }

    function render(input) {
      clear();
      const accepted = new Set();
      if (disposed || !root.isConnected) {
        return accepted;
      }
      const counts = new Map();
      input.forEach((view) => counts.set(view.id, (counts.get(view.id) || 0) + 1));
      const verified = [];
      for (const view of input) {
        if (!Number.isSafeInteger(view.id) || view.id < 1 || counts.get(view.id) !== 1 || !colors.has(view.color)) {
          continue;
        }
        const ranges = documentMap.verify(view);
        if (!Array.isArray(ranges) || !ranges.length) {
          continue;
        }
        verified.push({ view, ranges });
        accepted.add(view.id);
      }
      // 修改文本节点前先记录交集快照，否则重叠的动态 DOM
      // Range 会随先前批注拆分节点而移动。
      const walker = doc.createTreeWalker(root, w.NodeFilter.SHOW_TEXT);
      const pieces = [];
      let node;
      while ((node = walker.nextNode())) {
        const intervals = [];
        for (const { view, ranges } of verified) {
          for (const range of ranges) {
            if (!range.intersectsNode(node)) {
              continue;
            }
            const start = range.startContainer === node ? range.startOffset : 0;
            const end = range.endContainer === node ? range.endOffset : node.length;
            if (start < end) {
              intervals.push({ start, end, view });
            }
          }
        }
        if (intervals.length) {
          pieces.push({ node, text: node.data, intervals });
        }
      }
      for (const { node, text, intervals } of pieces) {
        const boundaries = [...new Set([0, text.length, ...intervals.flatMap((i) => [i.start, i.end])])].sort(
          (a, b) => a - b,
        );
        const fragment = doc.createDocumentFragment();
        for (let i = 0; i < boundaries.length - 1; i++) {
          const start = boundaries[i],
            end = boundaries[i + 1];
          let decorated = doc.createTextNode(text.slice(start, end));
          intervals
            .filter((p) => p.start <= start && p.end >= end)
            .reverse()
            .forEach(({ view }) => {
              const mark = doc.createElement("mark");
              mark.className = `bd-annotation-highlight bd-annotation-color-${view.color}${hasComment(view) ? " bd-annotation-has-comment" : ""}`;
              mark.dataset.id = String(view.id);
              mark.appendChild(decorated);
              decorated = mark;
            });
          fragment.appendChild(decorated);
        }
        node.replaceWith(fragment);
      }
      views = verified.map((entry) => entry.view);
      relayout();
      return accepted;
    }

    function rows(ranges) {
      const rects = ranges.flatMap((range) => {
        const visible = Array.from(range.getClientRects()).filter((r) => r.width > 0 && r.height > 0);
        if (!visible.length) {
          const fallback = range.getBoundingClientRect();
          if (fallback.width > 0 && fallback.height > 0) {
            visible.push(fallback);
          }
        }
        return visible;
      });
      return rects
        .sort((a, b) => a.top - b.top || a.left - b.left)
        .reduce((result, rect) => {
          const last = result.at(-1);
          if (last && Math.abs(last.top - rect.top) < 2 && Math.abs(last.bottom - rect.bottom) < 2) {
            last.left = Math.min(last.left, rect.left);
            last.right = Math.max(last.right, rect.right);
          } else {
            result.push({ left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom });
          }
          return result;
        }, []);
    }

    function relayout(activeId = null) {
      layer?.remove();
      layer = null;
      if (disposed || !root.isConnected) {
        unwrap();
        return;
      }
      const bounds = root.getBoundingClientRect();
      const seen = new Set();
      for (const view of views) {
        const ranges = documentMap.verify(view);
        if (!Array.isArray(ranges)) {
          unwrap(view.id);
          continue;
        }
        if (!hasComment(view)) {
          continue;
        }
        const key = JSON.stringify(view.placement.spans);
        if (seen.has(key)) {
          continue;
        }
        seen.add(key);
        for (const [index, row] of rows(ranges).entries()) {
          if (!layer) {
            layer = doc.createElement("div");
            layer.className = "bd-annotation-comment-outline-layer";
            layer.setAttribute("aria-hidden", "true");
            root.appendChild(layer);
          }
          const outline = doc.createElement("div");
          outline.className = `bd-annotation-comment-outline bd-annotation-color-${view.color}${String(activeId) === String(view.id) ? " bd-annotation-comment-outline-active" : ""}`;
          outline.dataset.annotationId = String(view.id);
          outline.dataset.textTop = String(row.top);
          const gutter = index === 0 ? 9 : 0;
          Object.assign(outline.style, {
            top: `${row.top - bounds.top - gutter}px`,
            left: `${row.left - bounds.left}px`,
            width: `${row.right - row.left}px`,
            height: `${row.bottom - row.top + gutter}px`,
          });
          if (index === 0) {
            const trigger = doc.createElement("button");
            trigger.type = "button";
            trigger.className = "bd-annotation-comment-trigger";
            trigger.textContent = "评注";
            trigger.setAttribute("aria-label", "打开阅读批注");
            trigger.addEventListener("mouseup", (event) => event.stopPropagation());
            trigger.addEventListener("click", (event) => {
              event.preventDefault();
              event.stopPropagation();
              root.dispatchEvent(new w.CustomEvent("bd:annotation-focus", { detail: view.id }));
            });
            outline.appendChild(trigger);
          }
          layer.appendChild(outline);
        }
      }
    }
    function dispose() {
      clear();
      disposed = true;
    }
    return { render, relayout, clear, dispose };
  }
  window.AnnotationDisplay = { create };
})();
