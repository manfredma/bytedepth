import { Window } from "happy-dom";
import fs from "node:fs";

const css = fs.readFileSync("bytedepth-start/src/main/resources/static/css/annotation.css", "utf8");

describe("annotation responsive access", () => {
  for (const width of [390, 768]) {
    test(`U42_mobile${width}KeepsUnlocatedCommentsReachableThroughTheVisiblePanel`, () => {
      const viewport = new Window({ width, height: 844 });
      const style = viewport.document.createElement("style");
      style.textContent = css;
      viewport.document.head.append(style);
      viewport.document.body.innerHTML = `<div class="bd-annotation-toolbar-item"><button>划线评论</button></div><article class="bd-annotation-scope"><aside class="bd-annotation-sidebar bd-annotation-sidebar-open bd-annotation-feed-follow"><section class="bd-annotation-feed"><div class="bd-annotation-feed-spacer"></div><article class="bd-annotation-feed-item">可定位评论</article><section class="bd-annotation-unresolved-comments"><article class="bd-annotation-unresolved-item">已删除原文的评论</article></section></section></aside></article>`;
      const panel = viewport.document.querySelector("aside");
      const computed = (selector) => viewport.getComputedStyle(viewport.document.querySelector(selector));
      expect(computed(".bd-annotation-toolbar-item").display).not.toBe("none");
      expect(viewport.getComputedStyle(panel).display).not.toBe("none");
      expect(viewport.getComputedStyle(panel).position).toBe("fixed");
      expect(viewport.getComputedStyle(panel).bottom).toBe("12px");
      expect(computed(".bd-annotation-feed-item").position).toBe("relative");
      expect(computed(".bd-annotation-feed-spacer").display).toBe("none");
      panel.classList.remove("bd-annotation-sidebar-open");
      expect(viewport.getComputedStyle(panel).opacity).toBe("0");
      expect(viewport.getComputedStyle(panel).pointerEvents).toBe("none");
    });
  }
});
