(() => {
  "use strict";
  const article = document.querySelector("#post-article .content");
  if (!article) {
    return;
  }

  const fallbackCopy = (text) => {
    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.setAttribute("readonly", "");
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();
    const copied = typeof document.execCommand === "function" && document.execCommand("copy");
    textarea.remove();
    if (!copied) {
      throw new Error("Clipboard is unavailable");
    }
  };

  const copyText = async (button, text) => {
    try {
      if (navigator.clipboard && typeof navigator.clipboard.writeText === "function") {
        await navigator.clipboard.writeText(text);
      } else {
        fallbackCopy(text);
      }
      button.textContent = "已复制";
    } catch {
      button.textContent = "复制失败";
    }
  };

  const contentRendered = (code) => {
    const id = code.dataset.annotationSegment;
    article.dispatchEvent(new CustomEvent("bd:content-rendered", { detail: { segmentIds: id ? [id] : [] } }));
  };
  const geometryChanged = () => article.dispatchEvent(new CustomEvent("bd:content-geometry"));
  const highlightCode = (code) => {
    const source = code.textContent;
    const prism = window.Prism;
    try {
      if (prism && typeof prism.highlightElement === "function") {
        prism.highlightElement(code);
      }
    } catch {
      // A failed renderer can leave half-written tokens. Restore the pure source
      // before notifying annotations, so other content never waits for this block.
      code.textContent = source;
    } finally {
      contentRendered(code);
    }
  };

  article.querySelectorAll(".bd-code-block").forEach((block) => {
    const code = block.querySelector("pre code");
    if (code) {
      highlightCode(code);
    }

    const copyButton = block.querySelector(".bd-code-block__copy");
    if (copyButton) {
      copyButton.addEventListener("click", () => {
        void copyText(copyButton, code ? code.textContent : "");
      });
    }

    const toggleButton = block.querySelector(".bd-code-block__toggle");
    if (toggleButton) {
      const body = block.querySelector(".bd-code-block__body");
      const setExpanded = (expanded) => {
        toggleButton.setAttribute("aria-expanded", String(expanded));
        toggleButton.setAttribute("aria-label", expanded ? "收起代码" : "展开代码");
        toggleButton.textContent = expanded ? "收起" : "展开";
        block.classList.toggle("bd-code-block--collapsed", !expanded);
        if (body) {
          body.hidden = !expanded;
        }
        geometryChanged();
      };
      setExpanded(toggleButton.getAttribute("aria-expanded") === "true");
      toggleButton.addEventListener("click", () => {
        setExpanded(toggleButton.getAttribute("aria-expanded") !== "true");
      });
    }
  });

  article.querySelectorAll(".bd-code-tabs").forEach((tabs) => {
    const buttons = Array.from(tabs.querySelectorAll(".bd-code-tabs__tab"));
    const panels = Array.from(tabs.querySelectorAll(".bd-code-tabs__panel"));
    const activate = (index) => {
      buttons.forEach((button, buttonIndex) => {
        button.setAttribute("aria-selected", String(buttonIndex === index));
      });
      panels.forEach((panel, panelIndex) => {
        const active = panelIndex === index;
        panel.classList.toggle("bd-code-tabs__panel--active", active);
        panel.setAttribute("aria-hidden", String(!active));
        panel.hidden = !active;
      });
      geometryChanged();
    };
    buttons.forEach((button, index) => {
      button.addEventListener("click", () => activate(index));
    });
    const initialIndex = buttons.findIndex((button) => button.getAttribute("aria-selected") === "true");
    activate(initialIndex >= 0 ? initialIndex : 0);
  });
})();
