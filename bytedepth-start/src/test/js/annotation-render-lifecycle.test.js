import { afterEach, beforeEach, expect, test, vi } from "vitest";
import "../../main/resources/static/js/annotation-render-lifecycle.js";
let root, queue, sequence, instances;
const settle = async () => {
  await Promise.resolve();
  await Promise.resolve();
};
function flush() {
  const callbacks = [...queue.values()];
  queue.clear();
  callbacks.forEach((callback) => callback(0));
}
beforeEach(() => {
  document.body.innerHTML = '<article><span data-annotation-segment="s0">正文</span></article>';
  root = document.querySelector("article");
  queue = new Map();
  sequence = 0;
  instances = [];
  vi.spyOn(window, "requestAnimationFrame").mockImplementation((callback) => {
    queue.set(++sequence, callback);
    return sequence;
  });
  vi.spyOn(window, "cancelAnimationFrame").mockImplementation((id) => queue.delete(id));
});
afterEach(() => {
  instances.forEach((instance) => instance.dispose());
  vi.restoreAllMocks();
});
function create(callbacks) {
  const lifecycle = window.AnnotationRenderLifecycle.create(root, callbacks);
  instances.push(lifecycle);
  return lifecycle;
}

test("U29_U32_batchesContentAndIgnoresItsOwnPaintMutationsWithoutAnObserverLoop", async () => {
  const rebuild = vi.fn(() => {
    root.firstChild.innerHTML = "<mark>正文</mark>";
  });
  const relayout = vi.fn();
  const lifecycle = create({ rebuild, relayout });
  lifecycle.contentChanged(["s0"]);
  lifecycle.contentChanged(["s1"]);
  lifecycle.geometryChanged();
  expect(queue.size).toBe(1);
  flush();
  await settle();
  expect(rebuild).toHaveBeenCalledTimes(1);
  expect(new Set(rebuild.mock.calls[0][0])).toEqual(new Set(["s0", "s1"]));
  expect(relayout).not.toHaveBeenCalled();
  expect(queue.size).toBe(0);
  lifecycle.mutate(() => {
    root.firstChild.innerHTML = "<mark>自己重新绘制</mark>";
  });
  await settle();
  expect(queue.size).toBe(0);
  root.firstChild.innerHTML = "<strong>渲染器替换正文</strong>";
  await settle();
  expect(queue.size).toBe(1);
  flush();
  await settle();
  expect(rebuild).toHaveBeenCalledTimes(2);
  expect(queue.size).toBe(0);
});

test("U19_U30_disposeDuringPendingRenderDropsCancelledCallbacksAndDetachedRoots", () => {
  const rebuild = vi.fn(),
    relayout = vi.fn();
  const lifecycle = create({ rebuild, relayout });
  lifecycle.contentChanged(["s0"]);
  const stale = [...queue.values()][0];
  lifecycle.dispose();
  lifecycle.dispose();
  stale(0);
  lifecycle.contentChanged(["s0"]);
  lifecycle.geometryChanged();
  lifecycle.mutate(() => root.append("旧任务"));
  expect(rebuild).not.toHaveBeenCalled();
  expect(relayout).not.toHaveBeenCalled();
  expect(root.textContent).toBe("正文");
  expect(queue.size).toBe(0);
  const fresh = create({ rebuild, relayout });
  fresh.contentChanged();
  root.remove();
  flush();
  expect(rebuild).not.toHaveBeenCalled();
});

test("U31_geometryEventsOnlyRelayoutAndPendingExternalMutationsAreNotDiscarded", async () => {
  const rebuild = vi.fn(),
    relayout = vi.fn();
  const lifecycle = create({ rebuild, relayout });
  lifecycle.geometryChanged();
  lifecycle.geometryChanged();
  flush();
  expect(relayout).toHaveBeenCalledTimes(1);
  expect(rebuild).not.toHaveBeenCalled();
  root.firstChild.textContent = "先发生的外部更新";
  lifecycle.mutate(() => {
    lifecycle.mutate(() => root.firstChild.append("自己的标记"));
  });
  await settle();
  flush();
  await settle();
  expect(rebuild).toHaveBeenCalledTimes(1);
  expect(queue.size).toBe(0);
  root.dispatchEvent(new CustomEvent("bd:content-rendered", { detail: { segmentIds: ["s0"] } }));
  flush();
  expect(rebuild).toHaveBeenCalledTimes(2);
  root.dispatchEvent(new CustomEvent("bd:content-geometry"));
  flush();
  expect(relayout).toHaveBeenCalledTimes(2);
});

test("U14_U31_imageAndToolbarChangesOnlyRelayoutAndUnknownReplacementRebuilds", async () => {
  const rebuild = vi.fn(),
    relayout = vi.fn();
  create({ rebuild, relayout });
  root.insertAdjacentHTML("beforeend", '<header class="bd-code-block__header">复制</header><img alt="">');
  await settle();
  flush();
  rebuild.mockClear();
  root.querySelector("header").firstChild.data = "已复制";
  root.querySelector("img").dispatchEvent(new Event("load"));
  await settle();
  flush();
  expect(relayout).toHaveBeenCalledTimes(1);
  expect(rebuild).not.toHaveBeenCalled();
  root.append("未知内容");
  await settle();
  flush();
  expect(rebuild).toHaveBeenCalledWith([]);
  root.dispatchEvent(new CustomEvent("bd:content-rendered"));
  flush();
  expect(rebuild).toHaveBeenCalledTimes(2);
});

test("U30_disposeReleasesAllListenersAndNestedMutationCanDisposeOrThrow", () => {
  const add = vi.spyOn(root, "addEventListener"),
    remove = vi.spyOn(root, "removeEventListener");
  const lifecycle = create({ rebuild: vi.fn(), relayout: vi.fn() });
  expect(() =>
    lifecycle.mutate(() => {
      throw new Error("paint error");
    }),
  ).toThrow("paint error");
  lifecycle.mutate(() => lifecycle.dispose());
  expect(remove.mock.calls).toEqual(add.mock.calls);
  root.dispatchEvent(new CustomEvent("bd:content-rendered"));
  root.dispatchEvent(new Event("load"));
  expect(queue.size).toBe(0);
});
