import fs from "node:fs";
import { JSDOM } from "jsdom";
import { afterEach, expect, test, vi } from "vitest";
import "../../main/resources/static/js/annotation-document.js";
import "../../main/resources/static/js/post-mermaid.js";
const mapping = window.AnnotationDocument;
const managers = [],
  windows = [];
const source = "graph LR\nA[起点] --> B[终点]\n";
const fixture = fs.readFileSync("scripts/test-fixtures/annotation/mermaid-replacement.html", "utf8");
const settle = async () => {
  await Promise.resolve();
  await Promise.resolve();
};
function boot(two = false) {
  const dom = new JSDOM(
    '<article id="post-article"><div class="content"><span data-annotation-segment="s0">图前</span><div class="bd-code-block"><div class="bd-code-block__body"><pre><code class="language-mermaid" data-annotation-segment="s1"></code></pre></div></div><span data-annotation-segment="s2">图后</span></div></article>',
    { runScripts: "outside-only" },
  );
  const w = dom.window;
  windows.push(w);
  const root = w.document.querySelector(".content");
  root.querySelector("code").textContent = source;
  for (const f of ["trim-range", "text-range"])
    w.eval(fs.readFileSync(`bytedepth-start/src/main/resources/static/vendor/hypothesis-anchoring/${f}.js`, "utf8"));
  if (two)
    root.insertAdjacentHTML(
      "beforeend",
      '<pre><code class="language-mermaid" data-annotation-segment="s3">graph LR\nC --> D</code></pre>',
    );
  const jobs = [];
  const library = {
    initialize: vi.fn(),
    run: vi.fn(
      ({ nodes: [node] }) =>
        new Promise((resolve, reject) => {
          jobs.push({
            node,
            resolve() {
              node.innerHTML = new w.DOMParser()
                .parseFromString(fixture, "text/html")
                .querySelector(".mermaid").innerHTML;
              resolve();
            },
            reject,
          });
        }),
    ),
  };
  const manager = window.PostMermaid.create(root, library);
  managers.push(manager);
  return { w, root, library, jobs, manager };
}
afterEach(() => {
  managers.splice(0).forEach((m) => m.dispose());
  windows.splice(0).forEach((w) => w.close());
});

test("U17_U18_pendingAndSuccessfulMermaidNeverBlockProseOrExposeSvgTextAsAnAnchor", async () => {
  const { w, root, jobs, manager } = boot();
  const end = 3 + [...source].length;
  const map = mapping.create(root, {
    revision: 7,
    projectionVersion: 1,
    digest: "d".repeat(64),
    text: `图前\n${source}图后`,
    segments: [
      { id: "s0", blockId: "b0", kind: "prose", text: "图前", start: 0, end: 2 },
      { id: "s1", blockId: "b1", kind: "mermaid", text: source, start: 3, end },
      { id: "s2", blockId: "b2", kind: "prose", text: "图后", start: end, end: end + 2 },
    ],
  });
  const pending = manager.render();
  await settle();
  expect(jobs).toHaveLength(1);
  for (const [id, quote] of [
    ["s0", "图前"],
    ["s2", "图后"],
  ]) {
    const range = w.document.createRange();
    range.selectNodeContents(root.querySelector(`[data-annotation-segment="${id}"]`));
    expect(map.describe(range).exact).toBe(quote);
  }
  expect(root.querySelector("pre").hidden).toBe(true);
  jobs[0].resolve();
  await pending;
  expect(root.querySelector("svg foreignObject .nodeLabel").textContent).toBe("起点");
  const diagramRange = w.document.createRange();
  diagramRange.selectNodeContents(root.querySelector("svg .nodeLabel"));
  expect(map.describe(diagramRange)).toEqual({ error: "INVALID_MAPPING" });
  expect(root.querySelector("code").textContent).toBe(source);
  const after = w.document.createRange();
  after.selectNodeContents(root.querySelector('[data-annotation-segment="s2"]'));
  expect(map.describe(after)).toMatchObject({ exact: "图后", start: end, end: end + 2 });
});

test("U18_U19_outOfOrderSuccessFailureAndRepeatRenderKeepIndependentBlocks", async () => {
  const { root, library, jobs, manager } = boot(true);
  const complete = vi.fn();
  root.addEventListener("bd:content-rendered", complete);
  const pending = manager.render();
  await settle();
  expect(jobs).toHaveLength(2);
  await manager.render();
  expect(library.run).toHaveBeenCalledTimes(2);
  jobs[1].resolve();
  await settle();
  expect(root.querySelectorAll("svg")).toHaveLength(1);
  jobs[0].reject(new Error("bad diagram"));
  await pending;
  expect(root.querySelector("pre").hidden).toBe(false);
  expect(root.querySelector(".bd-mermaid-error").textContent).toContain("源码");
  expect(complete).toHaveBeenCalledTimes(2);
  expect(root.querySelectorAll("svg")).toHaveLength(1);
  manager.dispose();
  expect(root.querySelector("svg")).toBeNull();
  expect(root.querySelectorAll("pre code")).toHaveLength(2);
});

test("U19_disposeDuringPendingRenderCannotCommitIntoTheNextGeneration", async () => {
  const { root, library, jobs, manager } = boot();
  const complete = vi.fn();
  root.addEventListener("bd:content-rendered", complete);
  const old = manager.render();
  await settle();
  manager.dispose();
  const next = window.PostMermaid.create(root, library);
  managers.push(next);
  const pending = next.render();
  await settle();
  const current = root.innerHTML;
  jobs[0].resolve();
  await old;
  expect(root.innerHTML).toBe(current);
  expect(complete).not.toHaveBeenCalled();
  jobs[1].resolve();
  await pending;
  expect(complete).toHaveBeenCalledTimes(1);
  expect(root.querySelectorAll("svg")).toHaveLength(1);
});

test("U18_initializationFailureRestoresPlainSourceAndReportsCompletion", async () => {
  const { root, manager } = boot();
  manager.dispose();
  const pre = root.querySelector("pre"),
    code = pre.querySelector("code");
  delete code.dataset.annotationSegment;
  const initialize = vi.fn(() => {
    throw new Error("unavailable");
  });
  const run = vi.fn();
  const next = window.PostMermaid.create(root, { initialize, run });
  managers.push(next);
  const complete = vi.fn();
  root.addEventListener("bd:content-rendered", complete);
  await next.render();
  expect(run).not.toHaveBeenCalled();
  expect(pre.hidden).toBe(false);
  expect(code.textContent).toBe(source);
  expect(complete.mock.calls[0][0].detail).toEqual({ segmentIds: [] });
  expect(root.querySelector(".bd-mermaid-error")).not.toBeNull();
  next.dispose();
  expect(root.querySelector(".bd-mermaid-error")).toBeNull();
  await next.render();
});

for (const when of ["beforeRun", "success", "failure"]) {
  test(`U19_detach${when}PreventsLateNotifications`, async () => {
    const { root, jobs, manager, library } = boot();
    const complete = vi.fn();
    root.addEventListener("bd:content-rendered", complete);
    const pending = manager.render();
    if (when === "beforeRun") root.remove();
    else {
      await settle();
      root.remove();
      if (when === "success") jobs[0].resolve();
      else jobs[0].reject(new Error("late"));
    }
    await pending;
    expect(complete).not.toHaveBeenCalled();
    await manager.render();
    expect(library.run).toHaveBeenCalledTimes(when === "beforeRun" ? 0 : 1);
  });
}

test("U19_disposeBeforeRunAndLateFailureAreInert", async () => {
  const { root, jobs, manager, library } = boot();
  const complete = vi.fn();
  root.addEventListener("bd:content-rendered", complete);
  const pending = manager.render();
  manager.dispose();
  await pending;
  expect(library.run).not.toHaveBeenCalled();
  const next = window.PostMermaid.create(root, library);
  managers.push(next);
  const other = next.render();
  await settle();
  next.dispose();
  jobs[0].reject(new Error("obsolete"));
  await other;
  expect(complete).not.toHaveBeenCalled();
  expect(root.querySelector("pre").hidden).toBe(false);
  expect(root.querySelector(".bd-mermaid-error")).toBeNull();
});

test("U18_pageInitializerUsesCurrentRootAndDisposesPreviousGeneration", async () => {
  const ready = vi.spyOn(document, "readyState", "get").mockReturnValue("complete");
  document.body.innerHTML =
    '<article id="post-article"><div class="content"><pre><code class="language-mermaid"></code></pre></div></article>';
  document.querySelector("code").textContent = "graph LR;A-->B";
  window.mermaid = { initialize: vi.fn(), run: vi.fn().mockResolvedValue(undefined) };
  vi.resetModules();
  await import("../../main/resources/static/js/post-mermaid.js");
  await settle();
  expect(window.mermaid.run).toHaveBeenCalledTimes(1);
  window.initPostMermaid();
  await settle();
  expect(window.mermaid.run).toHaveBeenCalledTimes(2);
  delete window.mermaid;
  window.initPostMermaid();
  expect(document.querySelector("pre code").textContent).toBe("graph LR;A-->B");
  document.body.innerHTML = "";
  window.initPostMermaid();
  ready.mockRestore();
});

test("U18_loadingPageWaitsForDomReadyExactlyOnce", async () => {
  document.body.innerHTML = "";
  const ready = vi.spyOn(document, "readyState", "get").mockReturnValue("loading");
  vi.resetModules();
  await import("../../main/resources/static/js/post-mermaid.js");
  const init = vi.spyOn(window, "initPostMermaid");
  // 监听器在注册时捕获函数，因此验证实际渲染效果。
  document.body.innerHTML =
    '<article id="post-article"><div class="content"><pre><code class="language-mermaid">graph LR</code></pre></div></article>';
  window.mermaid = { initialize: vi.fn(), run: vi.fn().mockResolvedValue(undefined) };
  expect(window.mermaid.run).not.toHaveBeenCalled();
  document.dispatchEvent(new Event("DOMContentLoaded"));
  await settle();
  expect(window.mermaid.run).toHaveBeenCalledTimes(1);
  document.dispatchEvent(new Event("DOMContentLoaded"));
  await settle();
  expect(window.mermaid.run).toHaveBeenCalledTimes(1);
  delete window.mermaid;
  init();
  init.mockRestore();
  ready.mockRestore();
  document.body.innerHTML = "";
});
