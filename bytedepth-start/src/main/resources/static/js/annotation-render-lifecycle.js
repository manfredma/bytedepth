(function () {
  "use strict";
  function create(root, { rebuild, relayout }) {
    const w = root.ownerDocument.defaultView;
    let disposed = false;
    let generation = 1;
    let frame = 0;
    let depth = 0;
    let contentPending = false;
    const segments = new Set();
    const observer = new w.MutationObserver(changed);
    const observe = () => observer.observe(root, { childList: true, characterData: true, subtree: true });

    function schedule() {
      if (disposed || frame) {
        return;
      }
      const epoch = generation;
      frame = w.requestAnimationFrame(() => {
        frame = 0;
        if (epoch !== generation || !root.isConnected) {
          return;
        }
        const content = contentPending;
        const ids = [...segments];
        contentPending = false;
        segments.clear();
        mutate(() => {
          if (content) {
            rebuild(ids);
          } else {
            relayout();
          }
        });
      });
    }
    function contentChanged(ids = []) {
      if (disposed) {
        return;
      }
      ids.forEach((id) => segments.add(id));
      contentPending = true;
      schedule();
    }
    function geometryChanged() {
      if (disposed) {
        return;
      }
      schedule();
    }
    function changed(records) {
      for (const record of records) {
        const element = record.target.nodeType === w.Node.ELEMENT_NODE ? record.target : record.target.parentElement;
        if (element?.closest(".bd-code-block__header,.bd-code-block__lines")) {
          geometryChanged();
        } else {
          const id = element?.closest("[data-annotation-segment]")?.dataset.annotationSegment;
          contentChanged(id ? [id] : []);
        }
      }
    }
    function mutate(action) {
      if (disposed || !root.isConnected) {
        return;
      }
      // Drain external mutations before disconnecting; otherwise a geometry update
      // could swallow an earlier Prism replacement. Own synchronous paint is ignored.
      if (depth === 0) {
        changed(observer.takeRecords());
        observer.disconnect();
      }
      depth += 1;
      try {
        return action();
      } finally {
        depth -= 1;
        if (depth === 0 && !disposed) {
          observe();
        }
      }
    }
    const rendered = (event) => contentChanged(event.detail?.segmentIds || []);
    root.addEventListener("bd:content-rendered", rendered);
    root.addEventListener("bd:content-geometry", geometryChanged);
    root.addEventListener("load", geometryChanged, true);
    observe();
    function dispose() {
      disposed = true;
      generation += 1;
      observer.disconnect();
      observer.takeRecords();
      if (frame) {
        w.cancelAnimationFrame(frame);
      }
      frame = 0;
      root.removeEventListener("bd:content-rendered", rendered);
      root.removeEventListener("bd:content-geometry", geometryChanged);
      root.removeEventListener("load", geometryChanged, true);
    }
    return { contentChanged, geometryChanged, mutate, dispose };
  }
  window.AnnotationRenderLifecycle = { create };
})();
