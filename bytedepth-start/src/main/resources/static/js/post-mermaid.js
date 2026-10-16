/* Mermaid owns only its generated containers; source segments remain immutable. */
(function () {
  "use strict";
  function create(root, library) {
    const w = root.ownerDocument.defaultView;
    const entries = new Map();
    let disposed = false;
    let generation = 0;
    let initializationError;
    try {
      library.initialize({
        startOnLoad: false,
        theme: "base",
        themeVariables: {
          primaryColor: "#dae8fc",
          primaryTextColor: "#1c1917",
          primaryBorderColor: "#6c8ebf",
          lineColor: "#57534e",
          secondaryColor: "#f7f5f0",
          tertiaryColor: "#fff",
          background: "#ffffff",
          mainBkg: "#dae8fc",
          nodeBorder: "#6c8ebf",
          clusterBkg: "#f7f5f0",
          titleColor: "#1c1917",
          edgeLabelBackground: "#ffffff",
          activeTaskBkgColor: "#e94560",
          activeTaskBorderColor: "#c73652",
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: "13px",
        },
        flowchart: { useMaxWidth: true, htmlLabels: true },
        sequence: { useMaxWidth: true },
        gantt: { useMaxWidth: true },
      });
    } catch (error) {
      initializationError = error;
    }
    function restore(entry) {
      entry.div.remove();
      if (!entry.enhanced && entry.placeholder.parentNode) {
        entry.placeholder.replaceWith(entry.pre);
      }
      entry.pre.hidden = entry.hidden;
      if (entry.error) {
        entry.error.remove();
      }
    }
    function notify(code) {
      root.dispatchEvent(
        new w.CustomEvent("bd:content-rendered", {
          detail: { segmentIds: code.dataset.annotationSegment ? [code.dataset.annotationSegment] : [] },
        }),
      );
    }
    function render() {
      if (disposed || !root.isConnected) {
        return Promise.resolve();
      }
      const current = generation;
      const jobs = [];
      root.querySelectorAll("pre code.language-mermaid").forEach((code) => {
        if (entries.has(code)) {
          return;
        }
        const pre = code.parentElement;
        const div = root.ownerDocument.createElement("div");
        div.className = "mermaid";
        div.textContent = code.textContent;
        div.style.cssText = "background:var(--card);padding:16px 0;text-align:center;";
        const enhanced = !!pre.closest(".bd-code-block");
        const placeholder = root.ownerDocument.createComment("mermaid source");
        const entry = { pre, div, enhanced, placeholder, hidden: pre.hidden, error: null };
        entries.set(code, entry);
        if (enhanced) {
          pre.hidden = true;
          pre.parentElement.appendChild(div);
        } else {
          pre.replaceWith(placeholder);
          placeholder.after(div);
        }
        const job = Promise.resolve()
          .then(() => {
            if (disposed || current !== generation || !root.isConnected) {
              return;
            }
            if (initializationError) {
              throw initializationError;
            }
            return library.run({ nodes: [div] });
          })
          .then(
            () => {
              if (disposed || current !== generation || !root.isConnected) {
                return;
              }
              notify(code);
            },
            () => {
              if (disposed || current !== generation || !root.isConnected) {
                return;
              }
              restore(entry);
              const error = root.ownerDocument.createElement("p");
              error.className = "bd-mermaid-error";
              error.textContent = "图表暂时无法渲染，请查看源码。";
              pre.after(error);
              entry.error = error;
              notify(code);
            },
          );
        jobs.push(job);
      });
      return Promise.all(jobs);
    }
    function dispose() {
      if (disposed) {
        return;
      }
      disposed = true;
      generation++;
      entries.forEach(restore);
      entries.clear();
    }
    return { render, dispose };
  }
  window.PostMermaid = { create };
  let manager;
  window.initPostMermaid = function () {
    if (manager) {
      manager.dispose();
    }
    const root = document.querySelector("#post-article .content");
    if (!root || !window.mermaid) {
      return;
    }
    manager = create(root, window.mermaid);
    manager.render();
  };
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", window.initPostMermaid, { once: true });
  } else {
    window.initPostMermaid();
  }
})();
