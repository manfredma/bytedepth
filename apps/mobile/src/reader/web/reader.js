/* 仅用于原生阅读器的适配层；投影与 UTF-16 转换由 AnnotationDocument 负责。 */
(function () {
  "use strict";
  let input;
  let root;
  let mapping;
  let anchor;
  let observer;
  let disposed = true;
  let interacting = false;
  let settle;
  let cleanup = [];
  const failedImages = new Map();
  const points = (text) => Array.from(text);
  const allowed = new Set(
    "P DIV SPAN H1 H2 H3 H4 H5 H6 STRONG EM B I S DEL BLOCKQUOTE UL OL LI DL DT DD PRE CODE TABLE THEAD TBODY TFOOT TR TH TD BR HR A IMG BUTTON SUP SUB SECTION".split(
      " ",
    ),
  );
  function send(type, payload) {
    if (disposed) return;
    const raw = JSON.stringify({
      type,
      postId: input.content.postId,
      contentVersion: input.content.contentVersion,
      generation: input.generation,
      payload,
    });
    // 以原生解析器为准，避免发送过大的本地选区或目录。
    if (new TextEncoder().encode(raw).length <= 65536) window.ReactNativeWebView.postMessage(raw);
  }
  function listen(target, event, callback, options) {
    target.addEventListener(event, callback, options);
    cleanup.push(() => target.removeEventListener(event, callback, options));
  }
  function safeLink(href) {
    try {
      const url = new URL(href, input.siteOrigin);
      return url.protocol === "https:" && !url.username && !url.password;
    } catch {
      return false;
    }
  }
  function placeholder(image) {
    const span = document.createElement("span");
    span.className = "reader-image-missing";
    span.dataset.resourceKey = image.dataset.resourceKey || "";
    span.dataset.imageAlt = image.alt;
    span.textContent = image.alt ? `图片不可用：${image.alt}` : "图片不可用";
    return span;
  }
  function imageResource(src) {
    try {
      const absolute = new URL(src, input.siteOrigin).href;
      return input.content.resources.find((resource) => {
        try {
          return new URL(resource.url, input.siteOrigin).href === absolute;
        } catch {
          return false;
        }
      });
    } catch {
      return undefined;
    }
  }
  function sanitize(fragment) {
    for (const element of [...fragment.children]) {
      if (!allowed.has(element.tagName) || element.namespaceURI !== "http://www.w3.org/1999/xhtml") {
        element.remove();
        continue;
      }
      if (element.tagName === "IMG") {
        const resource = imageResource(element.getAttribute("src"));
        const image = document.createElement("img");
        image.alt = element.getAttribute("alt") || "";
        image.dataset.resourceKey = resource ? resource.key : "";
        if (!resource) send("renderError", { message: "IMAGE_UNAVAILABLE:unlisted" });
        element.replaceWith(placeholder(image));
        continue;
      }
      for (const attr of [...element.attributes]) {
        const name = attr.name;
        const value = attr.value;
        const keep =
          (name === "data-annotation-segment" && /^s[0-9]+$/.test(value)) ||
          (name === "class" && /^(?:(?:bd-code-[\w-]+|language-[\w-]+|footnote[\w-]*)\s*)+$/.test(value)) ||
          (name === "id" && /^[^\s"'<>]+$/.test(value)) ||
          ["title", "aria-label", "aria-hidden", "aria-expanded", "aria-selected", "role"].includes(name) ||
          (["colspan", "rowspan", "start"].includes(name) && /^[0-9]{1,4}$/.test(value)) ||
          (name === "href" && element.tagName === "A" && safeLink(value));
        if (!keep) element.removeAttribute(name);
      }
      if (element.tagName === "BUTTON") element.type = "button";
      sanitize(element);
    }
  }
  function segmentElement(id) {
    return root.querySelector(`[data-annotation-segment="${id}"]`);
  }
  function pointRange(target) {
    const segment = input.content.document.segments.find((s) => s.id === target.segmentId);
    if (!segment || segment.kind === "mermaid" || !segment.text.length) return null;
    const length = points(segment.text).length;
    const offset = Math.min(target.codePointOffset, length - 1);
    const ranges = mapping.ranges([{ segmentId: segment.id, start: offset, end: offset + 1 }]);
    if (!Array.isArray(ranges)) return null;
    const range = ranges[0];
    if (target.codePointOffset === length) range.collapse(false);
    return range;
  }
  function rectangle(range) {
    const rects = range.getClientRects ? [...range.getClientRects()] : [];
    return rects[0] || null;
  }
  function capture() {
    const segments = input.content.document.segments;
    // 优先采用 Android 的可见光标，由统一适配层处理代理对边界。
    const caret = document.caretRangeFromPoint && document.caretRangeFromPoint(24, window.innerHeight * 0.25);
    if (
      caret &&
      root.contains(caret.startContainer) &&
      caret.startContainer.nodeType === 3 &&
      caret.startOffset < caret.startContainer.length
    ) {
      const range = caret.cloneRange();
      const text = caret.startContainer.textContent;
      const width = text.codePointAt(caret.startOffset) > 0xffff ? 2 : 1;
      range.setEnd(caret.startContainer, caret.startOffset + width);
      const description = mapping.describe(range);
      if (!description.error) {
        const span = description.spans[0];
        const rect = rectangle(range);
        return {
          segmentId: span.segmentId,
          codePointOffset: span.start,
          viewportRatio: Math.max(0, Math.min(1, (rect ? rect.top : 0) / window.innerHeight)),
        };
      }
    }
    for (const segment of segments) {
      const range = pointRange({ segmentId: segment.id, codePointOffset: 0 });
      const rect = range && rectangle(range);
      if (rect && rect.bottom > 0)
        return {
          segmentId: segment.id,
          codePointOffset: 0,
          viewportRatio: Math.max(0, Math.min(1, rect.top / window.innerHeight)),
        };
    }
    return null;
  }
  function paint() {
    const layer = document.getElementById("reader-marks");
    layer.replaceChildren();
    const envelope = input.annotations;
    const content = input.content;
    if (
      !envelope ||
      envelope.postId !== content.postId ||
      envelope.contentVersion !== content.contentVersion ||
      envelope.projectionVersion !== content.projectionVersion ||
      envelope.digest !== content.digest
    )
      return;
    for (const annotation of envelope.annotations) {
      const ranges = mapping.verify(annotation);
      if (!Array.isArray(ranges)) continue;
      const color = ["blue", "yellow", "green", "red"].includes(annotation.color) ? annotation.color : "yellow";
      for (const range of ranges)
        for (const rect of range.getClientRects ? range.getClientRects() : []) {
          const line = document.createElement("i");
          line.className = `reader-mark reader-mark--${color}`;
          line.style.cssText = `left:${rect.left + window.scrollX}px;top:${rect.bottom + window.scrollY}px;width:${rect.width}px`;
          layer.append(line);
        }
    }
  }
  function correct() {
    if (disposed) return;
    if (!interacting && anchor) {
      const range = pointRange(anchor);
      const rect = range && rectangle(range);
      if (rect) window.scrollBy(0, rect.top - anchor.viewportRatio * window.innerHeight);
    }
    paint();
  }
  function position() {
    if (!anchor) return;
    const model = input.content.document;
    const segment = model.segments.find((s) => s.id === anchor.segmentId);
    if (!segment) return;
    const block = model.segments.filter((s) => s.blockId === segment.blockId);
    const precedingHeadings = [...root.querySelectorAll("h1,h2,h3,h4,h5,h6")]
      .filter((heading) => {
        const first = heading.querySelector("[data-annotation-segment]");
        const source = first && model.segments.find((s) => s.id === first.dataset.annotationSegment);
        return source && source.start <= segment.start;
      })
      .map((heading) => heading.textContent);
    send("position", {
      postId: input.content.postId,
      contentVersion: input.content.contentVersion,
      projectionVersion: model.projectionVersion,
      segmentId: segment.id,
      codePointOffset: anchor.codePointOffset,
      blockKind: segment.kind,
      blockText: points(model.text)
        .slice(block[0].start, block[block.length - 1].end)
        .join(""),
      precedingHeadings,
      viewportRatio: anchor.viewportRatio,
      progress: Math.max(
        0,
        Math.min(1, window.scrollY / Math.max(1, document.documentElement.scrollHeight - window.innerHeight)),
      ),
      updatedAt: new Date().toISOString(),
    });
  }
  function stopCorrection() {
    interacting = true;
    clearTimeout(settle);
    settle = setTimeout(() => {
      if (!disposed) {
        anchor = capture();
        interacting = false;
        position();
      }
    }, 160);
  }
  function mount(value) {
    dispose();
    disposed = false;
    input = value;
    root = document.querySelector("#post-article .content");
    document.documentElement.dataset.theme = value.theme === "dark" ? "midnight" : "daylight";
    document.documentElement.style.setProperty("--reader-scale", String(value.fontScale));
    const template = document.createElement("template");
    template.innerHTML = value.content.html;
    sanitize(template.content);
    root.replaceChildren(template.content);

    mapping = window.AnnotationDocument.create(root, {
      ...value.content.document,
      revision: value.content.contentVersion,
    });
    setImages(value.imageDataUris, {
      postId: value.content.postId,
      contentVersion: value.content.contentVersion,
      generation: value.generation,
    });
    anchor = value.resume.target;
    if (value.resume.kind === "heading" && anchor && !segmentElement(anchor.segmentId)?.closest("h1,h2,h3,h4,h5,h6"))
      anchor = null;
  }
  function start() {
    if (disposed) return;
    const entries = [...root.querySelectorAll("h1,h2,h3,h4,h5,h6")]
      .map((heading) => ({
        segmentId: heading.querySelector("[data-annotation-segment]")?.dataset.annotationSegment,
        title: heading.textContent,
        level: Number(heading.tagName.slice(1)),
      }))
      .filter((entry) => entry.segmentId);
    send("toc", { entries });
    listen(document, "selectionchange", () => {
      const selection = mapping.describe(window.getSelection());
      if (!selection.error && points(selection.exact).length <= 500) send("selection", selection);
    });
    listen(root, "click", (event) => {
      const link = event.target.closest("a");
      if (link) {
        event.preventDefault();
        const href = link.getAttribute("href");
        if (href && safeLink(href)) {
          if (href.startsWith("#")) {
            const target = document.getElementById(href.slice(1));
            if (target) {
              stopCorrection();
              target.scrollIntoView();
            }
          } else send("link", { href });
        }
      }
      const image = event.target.closest("img[data-resource-key]");
      if (image) {
        const preview = document.getElementById("reader-preview");
        const copy = document.createElement("img");
        copy.src = image.src;
        copy.alt = image.alt;
        listen(
          copy,
          "error",
          () => send("renderError", { message: `IMAGE_DECODE_FAILED:${image.dataset.resourceKey}` }),
          { once: true },
        );
        preview.replaceChildren(copy);
        preview.setAttribute("open", "");
      }
    });
    listen(document.getElementById("reader-preview"), "click", (event) => {
      event.currentTarget.removeAttribute("open");
      event.currentTarget.replaceChildren();
    });
    for (const event of ["touchstart", "wheel", "keydown"]) listen(window, event, stopCorrection, { passive: true });
    listen(
      window,
      "scroll",
      () => {
        if (disposed) return;
        if (interacting) {
          stopCorrection();
          anchor = capture();
        }
        // 重排或位置校正产生的滚动事件不得覆盖已保存的字符位置。
        // 触摸、滚轮、键盘和显式目录跳转才表示主动移动。
        position();
        paint();
      },
      { passive: true },
    );
    listen(window, "resize", correct);
    listen(root, "bd:content-rendered", correct);
    listen(root, "bd:content-geometry", correct);
    if (window.ResizeObserver) {
      observer = new ResizeObserver(correct);
      observer.observe(root);
    }
    if (document.fonts)
      document.fonts.ready.then(() => {
        if (!disposed) correct();
      });
    correct();
    anchor = anchor || capture();
    position();
    send("ready", {});
  }
  function jumpToSegment(segmentId, context) {
    if (
      disposed ||
      context.postId !== input.content.postId ||
      context.contentVersion !== input.content.contentVersion ||
      context.generation !== input.generation ||
      !input.content.document.segments.some((segment) => segment.id === segmentId)
    )
      return;
    const element = segmentElement(segmentId);
    if (!element) return;
    stopCorrection();
    element.scrollIntoView();
    anchor = { segmentId, codePointOffset: 0, viewportRatio: 0 };
    position();
  }
  function setImages(images, context) {
    if (
      disposed ||
      context.postId !== input.content.postId ||
      context.contentVersion !== input.content.contentVersion ||
      context.generation !== input.generation
    )
      return;
    // 原生侧仅提供已验证数据；分配 DOM 前再次校验被动图片传输。
    for (const target of root.querySelectorAll("[data-resource-key]")) {
      const key = target.dataset.resourceKey;
      const data = images[key];
      const valid =
        typeof data === "string" &&
        /^data:image\/(?:png|jpeg|gif|webp|svg\+xml);base64,[A-Za-z0-9+/]+={0,2}$/.test(data);
      if (!valid || failedImages.get(key) === data) {
        if (target.tagName === "IMG") target.replaceWith(placeholder(target));
        continue;
      }
      if (target.tagName === "IMG") continue;
      const image = document.createElement("img");
      image.dataset.resourceKey = key;
      image.alt = target.dataset.imageAlt;
      listen(
        image,
        "error",
        () => {
          if (disposed || !image.isConnected) return;
          failedImages.set(key, data);
          send("renderError", { message: `IMAGE_DECODE_FAILED:${key}` });
          image.replaceWith(placeholder(image));
          correct();
        },
        { once: true },
      );
      listen(image, "load", correct);
      image.src = data;
      target.replaceWith(image);
    }
    correct();
  }
  function setAnnotations(envelope) {
    if (disposed) return;
    input.annotations = envelope;
    paint();
  }
  function configure(settings) {
    if (disposed) return;
    anchor = capture() || anchor;
    document.documentElement.dataset.theme = settings.theme === "dark" ? "midnight" : "daylight";
    document.documentElement.style.setProperty("--reader-scale", String(settings.fontScale));
    correct();
  }
  function dispose() {
    disposed = true;
    cleanup.splice(0).forEach((fn) => fn());
    clearTimeout(settle);
    if (observer) observer.disconnect();
    observer = null;
    if (mapping) mapping.dispose();
    mapping = null;
    anchor = null;
    interacting = false;
    input = null;
    failedImages.clear();
    document.getElementById("reader-marks")?.replaceChildren();
    document.getElementById("reader-preview")?.replaceChildren();
  }
  window.MobileReader = Object.freeze({ mount, start, jumpToSegment, setImages, setAnnotations, configure, dispose });
})();
