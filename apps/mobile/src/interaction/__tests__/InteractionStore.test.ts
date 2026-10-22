import { InteractionStore } from "../InteractionStore";
import type { PublicContent, ReaderSelection } from "../../contracts/mobile";
import { MobileRequestError } from "../../api/errors";
const identity = {
  postId: "1",
  contentVersion: 1,
  projectionVersion: 1,
  digest: "digest",
  representationHash: "hash",
  shellVersion: "1",
};
const content = identity as PublicContent;
const selection: ReaderSelection = {
  exact: "字",
  prefix: "",
  suffix: "",
  start: 0,
  end: 1,
  spans: [{ segmentId: "s", start: 0, end: 1 }],
};
function setup() {
  const request = jest.fn();
  let clear = () => {};
  let user: any = null;
  const auth = {
    getUser: () => user,
    onPrivateClear: (fn: () => void) => {
      clear = fn;
      return () => {};
    },
  };
  const store = new InteractionStore({ request }, auth, () => "uuid", content);
  return {
    store,
    request,
    clear: () => clear(),
    login: () => {
      user = { id: "1", username: "reader", permissions: ["blog:comment:create"] };
    },
  };
}
it("allows anonymous rating and reads the authoritative score after changing it", async () => {
  const { store, request } = setup();
  request.mockResolvedValueOnce(undefined).mockResolvedValueOnce({ averageScore: 4, ratingCount: 1, visitorScore: 4 });
  await store.rate(4);
  expect(store.snapshot().rating!.visitorScore).toBe(4);
  expect(request.mock.calls.map((c) => c[0])).toEqual(["/posts/1/rating", "/posts/1/ratings"]);
});
it("clears private annotations synchronously and rejects late responses after logout", async () => {
  const { store, request, clear } = setup();
  let resolve: any;
  request.mockReturnValueOnce(
    new Promise((r) => {
      resolve = r;
    }),
  );
  const pending = store.loadAnnotations();
  clear();
  resolve({ ...identity, schemaVersion: 1, annotations: [{ id: "private" }] });
  await pending;
  expect(store.snapshot().annotations).toBeNull();
});
it("keeps the same draft and request ID after revision conflicts, but clears it on page destruction", async () => {
  const { store, request, login } = setup();
  login();
  store.select(selection, true);
  store.editDraft("说明", "PRIVATE", "#facc15");
  request.mockRejectedValueOnce({ status: 409 });
  await expect(store.saveDraft()).rejects.toEqual({ status: 409 });
  expect(store.snapshot().draft!.anchor.requestId).toBe("uuid");
  expect(store.snapshot().draft!.annotationText).toBe("说明");
  store.dispose();
  expect(store.snapshot().draft).toBeNull();
});
it("blocks old-version selection and offline writes, without an offline queue", async () => {
  const { store, login, request } = setup();
  login();
  expect(() => store.select(selection, false)).toThrow("确认更新");
  store.setOnline(false);
  await expect(store.rate(2)).rejects.toThrow("联网");
  expect(request).not.toHaveBeenCalled();
  expect(() => store.select(selection, true)).toThrow("联网");
});
it("enforces comment permission and text limits before network submission", async () => {
  const { store, login, request } = setup();
  await expect(store.comment("hello")).rejects.toThrow("登录");
  login();
  for (const text of ["", " ".repeat(2), "a".repeat(2001)]) await expect(store.comment(text)).rejects.toThrow("1–2000");
  request
    .mockResolvedValueOnce(undefined)
    .mockResolvedValueOnce({ items: [{ id: "c", content: "hello" }], hasNext: false });
  await store.comment(" hello ");
  expect(store.snapshot().comments[0].content).toBe("hello");
  expect(request.mock.calls[0][1].body).toEqual({ content: "hello" });
});
it("denies comments when a signed-in account lacks permission", async () => {
  const store = new InteractionStore(
    { request: jest.fn() },
    { getUser: () => ({ id: "2", username: "read", permissions: [] }), onPrivateClear: () => () => {} },
    () => "uuid",
    content,
  );
  await expect(store.comment("hello")).rejects.toThrow("没有评论权限");
});
it("appends comment pages and loads visible annotations in memory", async () => {
  const { store, request, login } = setup();
  const listener = jest.fn();
  const unsubscribe = store.subscribe(listener);
  request
    .mockResolvedValueOnce({ items: [{ id: "a" }], hasNext: true })
    .mockResolvedValueOnce({ items: [{ id: "b" }], hasNext: false });
  await store.loadComments();
  await store.loadComments(2);
  expect(store.snapshot().comments.map((c) => c.id)).toEqual(["a", "b"]);
  expect(store.snapshot().hasMoreComments).toBe(false);
  login();
  request.mockResolvedValueOnce({ ...identity, schemaVersion: 1, annotations: [] });
  await store.loadAnnotations();
  expect(store.snapshot().annotations?.postId).toBe("1");
  expect(listener).toHaveBeenCalled();
  unsubscribe();
});
it("creates, edits and deletes owned annotations then reloads the server list", async () => {
  const { store, request, login } = setup();
  login();
  store.editDraft("ignored", "PUBLIC", "blue");
  await expect(store.saveDraft()).rejects.toThrow("选择原文");
  store.select(selection, true);
  store.editDraft("x".repeat(2001), "PUBLIC", "blue");
  await expect(store.saveDraft()).rejects.toThrow("2000");
  store.cancelDraft();
  store.select(selection, true);
  store.editDraft("补充", "PUBLIC", "blue");
  request.mockResolvedValue({ annotations: [] });
  await store.saveDraft();
  expect(store.snapshot().draft).toBeNull();
  const annotation = { id: "7", ownedByCurrentVisitor: true } as any;
  await store.update(annotation, "修改", "PRIVATE");
  await store.remove(annotation);
  expect(request.mock.calls.map((c) => c[1].method)).toEqual(["POST", "GET", "PATCH", "GET", "DELETE", "GET"]);
  await expect(store.update({ ...annotation, ownedByCurrentVisitor: false }, "x", "PRIVATE")).rejects.toThrow("本人");
  await expect(store.remove({ ...annotation, ownedByCurrentVisitor: false })).rejects.toThrow("本人");
  await expect(store.update(annotation, "x".repeat(2001), "PRIVATE")).rejects.toThrow("2000");
});
it("rejects invalid scores and all operations after the page is destroyed", async () => {
  const { store } = setup();
  await expect(store.rate(0 as never)).rejects.toThrow("1–5");
  store.dispose();
  await expect(store.loadRating()).rejects.toThrow("联网");
});
it.each(["loadRating", "rate", "loadComments", "comment", "loadAnnotations", "saveDraft", "update", "remove"] as const)(
  "ignores a delayed %s response from a previous private generation",
  async (method) => {
    const { store, request, login, clear } = setup();
    login();
    store.select(selection, true);
    let resolve!: (value: any) => void;
    request.mockReturnValueOnce(
      new Promise((r) => {
        resolve = r;
      }),
    );
    let pending: Promise<void>;
    if (method === "rate") pending = store.rate(3);
    else if (method === "comment") pending = store.comment("评论");
    else if (method === "update")
      pending = store.update({ id: "1", ownedByCurrentVisitor: true } as any, "修改", "PUBLIC");
    else if (method === "remove") pending = store.remove({ id: "1", ownedByCurrentVisitor: true } as any);
    else pending = store[method]();
    clear();
    resolve({ items: [], hasNext: false, annotations: [], visitorScore: 3 });
    await pending;
    expect(store.snapshot().draft).toBeNull();
    expect(store.snapshot().annotations).toBeNull();
    expect(store.snapshot().comments).toEqual([]);
    expect(store.snapshot().rating).toBeNull();
    expect(request).toHaveBeenCalledTimes(1);
  },
);
it("deduplicates overlapping comment pages when new comments move offset boundaries", async () => {
  const { store, request } = setup();
  request.mockResolvedValueOnce({ items: [{ id: "a", content: "first" }], hasNext: true }).mockResolvedValueOnce({
    items: [
      { id: "a", content: "first" },
      { id: "b", content: "second" },
    ],
    hasNext: false,
  });
  await store.loadComments();
  await store.loadComments(2);
  expect(store.snapshot().comments.map((c) => c.id)).toEqual(["a", "b"]);
});
it("respects server Retry-After before allowing an explicit rating retry", async () => {
  const { store, request } = setup();
  const time = jest.spyOn(Date, "now").mockReturnValue(1000);
  request.mockRejectedValueOnce(new MobileRequestError(429, "RATE_LIMITED", "稍后重试", 5000));
  await expect(store.rate(4)).rejects.toMatchObject({ status: 429 });
  await expect(store.rate(4)).rejects.toMatchObject({ status: 429 });
  expect(request).toHaveBeenCalledTimes(1);
  time.mockReturnValue(6000);
  request.mockResolvedValueOnce(undefined).mockResolvedValueOnce({ averageScore: 4, ratingCount: 1, visitorScore: 4 });
  await store.rate(4);
  expect(store.snapshot().rating?.visitorScore).toBe(4);
  time.mockRestore();
});
