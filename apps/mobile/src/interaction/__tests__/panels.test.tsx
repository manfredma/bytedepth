import React from "react";
import { Alert } from "react-native";
import { render, screen, fireEvent, act, waitFor } from "@testing-library/react-native";
import { AnnotationsPanel, CommentsPanel, RatingPanel, AnnotationEditor } from "../panels";
import { InteractionStore } from "../InteractionStore";
import { createFixture, content } from "../../screens/__tests__/fixtures";
import { MobileRequestError } from "../../api/errors";
import type { MobileAnnotation } from "../../contracts/mobile";
const annotation: MobileAnnotation = {
  id: "7",
  postId: "1",
  selectedText: "原文引用",
  annotationText: "已有评注",
  annotationHtml: "",
  color: "blue",
  visibility: "PRIVATE",
  createdAt: "2026-10-22T00:00:00Z",
  ownedByCurrentVisitor: true,
  anchor: null,
  placement: {
    annotationId: "7",
    targetRevision: 1,
    projectionVersion: 1,
    resolverVersion: 1,
    status: "ANCHORED",
    spans: [],
    reason: "EXACT",
  },
  targetDigest: "digest",
  migrationBaselineRevision: null,
};
const envelope = {
  schemaVersion: 1,
  postId: "1",
  contentVersion: 1,
  projectionVersion: 1,
  digest: "digest",
  annotations: [],
};
it("keeps a create draft in the same editor after 409/422, reuses its request ID, and clears it after successful creation", async () => {
  const f = await createFixture();
  await f.auth.login("reader", "password");
  const store = new InteractionStore(f.runtime.api, f.auth, f.runtime.uuid, content);
  store.select(
    { exact: "原文", prefix: "", suffix: "", start: 0, end: 2, spans: [{ segmentId: "s", start: 0, end: 2 }] },
    true,
  );
  let status = 409;
  f.request.mockImplementation(async (_path: string, options?: any) => {
    if (options?.method === "POST" && status) throw new MobileRequestError(status, "STALE_REVISION", "changed");
    return envelope;
  });
  await render(<AnnotationsPanel store={store} matches current />, { wrapper: f.provider });
  await fireEvent.changeText(screen.getByLabelText("评注文字"), "保留草稿");
  await fireEvent.press(screen.getByRole("button", { name: "公开" }));
  for (const name of ["补充说明", "重点摘录", "实践结论", "疑问待办"])
    await fireEvent.press(screen.getByRole("button", { name }));
  await fireEvent.press(screen.getByRole("button", { name: "仅自己" }));
  await fireEvent.press(screen.getByRole("button", { name: "保存评注" }));
  expect(await screen.findByText("正文或选区已变化，请刷新后重新选择。草稿仍保留在本页。")).toBeOnTheScreen();
  expect(screen.getByLabelText("评注文字").props.value).toBe("保留草稿");
  status = 422;
  await fireEvent.press(screen.getByRole("button", { name: "保存评注" }));
  expect(await screen.findByText("这段原文无法定位，请重新选择。草稿仍保留在本页。")).toBeOnTheScreen();
  const attempts = f.request.mock.calls.filter(
    (c) => c[0] === "/posts/1/annotations" && (c as any)[1]?.method === "POST",
  ) as any[];
  expect(attempts[0][1].body.anchor.requestId).toBe(attempts[1][1].body.anchor.requestId);
  status = 0;
  await fireEvent.press(screen.getByRole("button", { name: "保存评注" }));
  await waitFor(() => expect(screen.queryByLabelText("评注文字")).toBeNull());
  expect(store.snapshot().draft).toBeNull();
  await act(() => store.dispose());
});
it("shows stale annotations as references and edits an owned annotation in place with delete confirmation", async () => {
  const f = await createFixture();
  await f.auth.login("reader", "password");
  const store = new InteractionStore(f.runtime.api, f.auth, f.runtime.uuid, content);
  let rows = [annotation];
  f.request.mockImplementation(async (_path: string, options?: any) => {
    if (options?.method === "PATCH")
      rows = [{ ...annotation, annotationText: options.body.annotationText, visibility: options.body.visibility }];
    if (options?.method === "DELETE") rows = [];
    return { ...envelope, annotations: rows };
  });
  await render(<AnnotationsPanel store={store} matches={false} current={false} />, { wrapper: f.provider });
  await screen.findByText("已有评注");
  expect(screen.getByText("批注对应当前新版，刷新正文后才能显示划线；以下保留原始引用。")).toBeOnTheScreen();
  await fireEvent.press(screen.getByRole("button", { name: "编辑评注" }));
  await fireEvent.changeText(screen.getByLabelText("评注文字"), "修改内容");
  await fireEvent.press(screen.getByRole("button", { name: "公开" }));
  await fireEvent.press(screen.getByRole("button", { name: "取消编辑" }));
  expect(screen.getByText("已有评注")).toBeOnTheScreen();
  await fireEvent.press(screen.getByRole("button", { name: "编辑评注" }));
  await fireEvent.press(screen.getByRole("button", { name: "保存评注" }));
  await screen.findByText("修改内容");
  const confirm = jest.spyOn(Alert, "alert");
  await fireEvent.press(screen.getByRole("button", { name: "删除评注" }));
  expect(confirm).toHaveBeenCalledWith("删除这条评注？", expect.any(String), expect.any(Array));
  const remove = confirm.mock.calls[0][2]![1].onPress!;
  await act(() => remove());
  await screen.findByText("暂无评注");
  await act(() => store.dispose());
  confirm.mockRestore();
});
it("hides private editor and clears it on account expiry without retaining an offline queue", async () => {
  const f = await createFixture();
  await f.auth.login("reader", "password");
  const store = new InteractionStore(f.runtime.api, f.auth, f.runtime.uuid, content);
  store.select(
    { exact: "字", prefix: "", suffix: "", start: 0, end: 1, spans: [{ segmentId: "s", start: 0, end: 1 }] },
    true,
  );
  await render(<AnnotationsPanel store={store} matches current />, { wrapper: f.provider });
  await fireEvent.changeText(screen.getByLabelText("评注文字"), "私有草稿");
  await act(() => f.setOnline(false));
  expect(screen.queryByLabelText("评注文字")).toBeNull();
  expect(screen.getByText("评注需要联网")).toBeOnTheScreen();
  await act(() => store.dispose());
});
it("requires comment permission, publishes only while online, and appends approved comment pages", async () => {
  const f = await createFixture();
  const store = new InteractionStore(f.runtime.api, f.auth, f.runtime.uuid, content);
  let created = false;
  f.request.mockImplementation(async (path: string, options?: any) => {
    if (path === "/me") return { id: "1", username: "reader", permissions: ["blog:comment:create"] };
    if (options?.method === "POST") {
      created = true;
      return undefined;
    }
    return {
      items: created ? [{ id: "c", content: "有用的理解", username: "reader", createdAt: "2026-10-22T00:00:00Z" }] : [],
      hasNext: !path.endsWith("page=2"),
    };
  });
  await render(<CommentsPanel store={store} />, { wrapper: f.provider });
  await screen.findByText("登录后可评论");
  expect(screen.getByRole("button", { name: "发表评论" })).toBeDisabled();
  await act(() => f.auth.login("reader", "password"));
  await fireEvent.changeText(screen.getByLabelText("评论内容"), "有用的理解");
  await fireEvent.press(screen.getByRole("button", { name: "发表评论" }));
  expect(await screen.findByText("有用的理解")).toBeOnTheScreen();
  await fireEvent.press(screen.getByRole("button", { name: "更多评论" }));
  await waitFor(() => expect(screen.queryByRole("button", { name: "更多评论" })).toBeNull());
  await act(() => f.setOnline(false));
  expect(screen.getByLabelText("评论内容").props.value).toBe("");
  expect(screen.getByText("评论需要联网")).toBeOnTheScreen();
  await act(() => store.dispose());
});
it("shows denied commenting permission and failure without creating a local write queue", async () => {
  const f = await createFixture();
  f.request.mockResolvedValue({ id: "2", username: "readonly", permissions: [] });
  await f.auth.login("readonly", "password");
  f.request.mockRejectedValue(new Error("评论暂不可用"));
  const store = new InteractionStore(f.runtime.api, f.auth, f.runtime.uuid, content);
  await render(<CommentsPanel store={store} />, { wrapper: f.provider });
  expect(await screen.findByText("评论暂不可用")).toBeOnTheScreen();
  expect(screen.getByText("当前账号没有评论权限")).toBeOnTheScreen();
  expect(screen.getByRole("button", { name: "发表评论" })).toBeDisabled();
  await act(() => store.dispose());
});
it("shows rating errors and has no interactive offline rating submission", async () => {
  const f = await createFixture();
  f.request.mockRejectedValue(new Error("评分暂不可用"));
  const store = new InteractionStore(f.runtime.api, f.auth, f.runtime.uuid, content);
  await render(<RatingPanel store={store} />, { wrapper: f.provider });
  expect(await screen.findByText("评分暂不可用")).toBeOnTheScreen();
  await act(() => f.setOnline(false));
  expect(screen.getByRole("button", { name: "1 分" })).toBeDisabled();
  await act(() => store.dispose());
});
it("shows public empty-note excerpts to guests without editing controls", async () => {
  const f = await createFixture();
  f.request.mockResolvedValue({
    ...envelope,
    annotations: [{ ...annotation, annotationText: "", visibility: "PUBLIC", ownedByCurrentVisitor: false }],
  });
  const store = new InteractionStore(f.runtime.api, f.auth, f.runtime.uuid, content);
  await render(<AnnotationsPanel store={store} matches current />, { wrapper: f.provider });
  await screen.findByText("划线摘录");
  expect(screen.getByText("原文引用").parent).toHaveStyle({ borderBottomColor: "#315efb" });
  expect(screen.getByText("登录后长按原文，选择文字可新建评注")).toBeOnTheScreen();
  expect(screen.queryByRole("button", { name: "编辑评注" })).toBeNull();
  await act(() => store.dispose());
});
it("cancels an in-memory draft and ignores a delayed panel failure after private state is cleared", async () => {
  const f = await createFixture();
  await f.auth.login("reader", "password");
  const store = new InteractionStore(f.runtime.api, f.auth, f.runtime.uuid, content);
  store.select(
    { exact: "字", prefix: "", suffix: "", start: 0, end: 1, spans: [{ segmentId: "s", start: 0, end: 1 }] },
    true,
  );
  const result = await render(<AnnotationsPanel store={store} matches current />, { wrapper: f.provider });
  await fireEvent.press(screen.getByRole("button", { name: "取消编辑" }));
  expect(store.snapshot().draft).toBeNull();
  await result.unmount();
  let reject!: (error: Error) => void;
  f.request.mockImplementation(
    () =>
      new Promise((_resolve, r) => {
        reject = r;
      }),
  );
  await render(<RatingPanel store={store} />, { wrapper: f.provider });
  await act(() => f.setOnline(false));
  await act(() => reject(new Error("旧面板错误")));
  expect(screen.queryByText("旧面板错误")).toBeNull();
  await act(() => store.dispose());
});
it("shows each annotation type in its established semantic color", async () => {
  const f = await createFixture();
  await render(
    <AnnotationEditor
      text=""
      visibility="PRIVATE"
      color="yellow"
      onChange={() => {}}
      onSave={() => {}}
      onCancel={() => {}}
      busy={false}
    />,
    { wrapper: f.provider },
  );
  for (const [name, color] of [
    ["补充说明", "#315efb"],
    ["重点摘录", "#b7791f"],
    ["实践结论", "#25855a"],
    ["疑问待办", "#ca5b56"],
  ])
    expect(screen.getByText(name)).toHaveStyle({ color });
});
