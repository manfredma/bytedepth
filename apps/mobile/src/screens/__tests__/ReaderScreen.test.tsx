import React from "react";
import { render, screen, fireEvent, act, waitFor } from "@testing-library/react-native";
import { ReaderScreen } from "../ReaderScreen";
import { createFixture, content, navigation } from "./fixtures";
import type { ReaderWebViewProps } from "../../reader/ReaderWebView";
import { Linking } from "react-native";
import { API_ORIGIN } from "../../config";
let mockReader: ReaderWebViewProps;
jest.mock("../../reader/ReaderWebView", () => ({
  ReaderWebView: (props: ReaderWebViewProps) => {
    mockReader = props;
    return null;
  },
}));
it("opens saved public content for a guest, persists position without resetting initial resume, and exposes no download control", async () => {
  const f = await createFixture();
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  expect(await screen.findByText("技术长文")).toBeOnTheScreen();
  const initial = mockReader.resume;
  await act(() =>
    mockReader.onMessage({
      type: "position",
      postId: "1",
      contentVersion: 1,
      generation: mockReader.generation,
      payload: {
        postId: "1",
        contentVersion: 1,
        projectionVersion: 1,
        segmentId: "s",
        codePointOffset: 1,
        blockKind: "prose",
        blockText: "文字",
        precedingHeadings: [],
        viewportRatio: 0,
        progress: 0.5,
        updatedAt: "2026-10-22T00:00:00Z",
      },
    }),
  );
  expect(f.reader.savePosition).toHaveBeenCalledTimes(1);
  expect(mockReader.resume).toBe(initial);
  expect(screen.queryByText("下载")).toBeNull();
  expect(screen.queryByText("清理缓存")).toBeNull();
});
it("offers anonymous rating and reloads its current score after a change", async () => {
  const f = await createFixture();
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  await fireEvent.press(screen.getByRole("button", { name: "评分" }));
  await screen.findByText("4.0 分 · 1 人评分");
  await fireEvent.press(screen.getByRole("button", { name: "5 分" }));
  await waitFor(() =>
    expect(f.request).toHaveBeenCalledWith(
      "/posts/1/rating",
      expect.objectContaining({ method: "PUT", body: { score: 5 } }),
    ),
  );
});
it("preserves the old body when a new version is found and blocks selection until refresh", async () => {
  const f = await createFixture();
  await f.auth.login("reader", "password");
  f.setInitial({
    postId: "1",
    copyState: "OFFLINE_READY",
    checkState: "UPDATE_AVAILABLE",
    content,
    lastConfirmedAt: null,
  });
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  await act(() =>
    mockReader.onMessage({
      type: "selection",
      postId: "1",
      contentVersion: 1,
      generation: mockReader.generation,
      payload: {
        exact: "文",
        prefix: "",
        suffix: "字",
        start: 0,
        end: 1,
        spans: [{ segmentId: "s", start: 0, end: 1 }],
      },
    }),
  );
  expect(await screen.findByText("请先确认更新，再新建批注")).toBeOnTheScreen();
  await fireEvent.press(screen.getByRole("button", { name: "有新版本，刷新" }));
  expect(f.reader.refresh).toHaveBeenCalledWith("1");
});
function send(type: any, payload: any, extra: Record<string, unknown> = {}) {
  mockReader.onMessage({
    type,
    payload,
    postId: "1",
    contentVersion: 1,
    generation: mockReader.generation,
    ...extra,
  } as never);
}
it("opens the chapter sheet, uses model IDs for jumps, and opens reading settings without replacing content", async () => {
  const f = await createFixture();
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  await fireEvent.press(screen.getByRole("button", { name: "目录" }));
  expect(screen.getByText("本文没有目录")).toBeOnTheScreen();
  await fireEvent.press(screen.getByRole("button", { name: "关闭" }));
  await act(() => send("toc", { entries: [{ segmentId: "s", title: "第一节", level: 1 }] }));
  for (let i = 1; i <= 2; i++) {
    await fireEvent.press(screen.getByRole("button", { name: "目录" }));
    await fireEvent.press(screen.getByText("第一节"));
    expect(mockReader.jumpTo).toEqual({ segmentId: "s", request: i });
  }
  await fireEvent.press(screen.getByRole("button", { name: "设置" }));
  await fireEvent.press(screen.getByRole("button", { name: "深色" }));
  expect(mockReader.theme).toBe("dark");
  await fireEvent.press(screen.getByRole("button", { name: "浅色" }));
  expect(mockReader.theme).toBe("light");
  await fireEvent(screen.getByTestId("reader-sheet"), "requestClose");
  expect(screen.queryByText("阅读偏好")).toBeNull();
});
it("passes private annotations only online, opens selection in the editor, and clears private layers on background", async () => {
  const f = await createFixture();
  await f.auth.login("reader", "password");
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  await fireEvent.press(screen.getByRole("button", { name: "评注" }));
  await screen.findByText("阅读评注");
  await fireEvent.press(screen.getByRole("button", { name: "关闭" }));
  await act(() =>
    send("selection", {
      exact: "文",
      prefix: "",
      suffix: "字",
      start: 0,
      end: 1,
      spans: [{ segmentId: "s", start: 0, end: 1 }],
    }),
  );
  expect(await screen.findByLabelText("评注文字")).toBeOnTheScreen();
  await fireEvent.changeText(screen.getByLabelText("评注文字"), "本机暂存私有草稿");
  await act(() => f.auth.clearPrivateState("background"));
  expect(screen.queryByLabelText("评注文字")).toBeNull();
  expect(mockReader.annotations?.annotations).toEqual([]);
  await act(() => f.setOnline(false));
  expect(mockReader.annotations).toBeNull();
  await fireEvent.press(screen.getByRole("button", { name: "评论" }));
  expect(screen.getByText("评论需要联网")).toBeOnTheScreen();
});
it("routes only HTTPS links through public resolution or the system browser and rejects stale bridge generations", async () => {
  const f = await createFixture();
  const base = f.request.getMockImplementation()!;
  f.request.mockImplementation(async (path: string) =>
    path.startsWith("/posts/resolve") ? { postId: "2" } : base(path),
  );
  const open = jest.spyOn(Linking, "openURL").mockResolvedValue(undefined);
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  for (const extra of [{ postId: "2" }, { contentVersion: 2 }, { generation: -1 }])
    await act(() => send("position", {}, extra));
  expect(f.reader.savePosition).not.toHaveBeenCalled();
  await act(() => send("link", { href: "/posts/new-slug" }));
  expect(navigation.push).toHaveBeenCalledWith("Reader", { postId: "2" });
  for (const href of ["https://example.org/resource", API_ORIGIN + "/about"]) await act(() => send("link", { href }));
  expect(open).toHaveBeenCalledWith("https://example.org/resource");
  for (const href of ["http://example.org", "https://user@example.org", "https://:password@example.org"]) {
    await act(() => send("link", { href }));
    expect(screen.getByText("此链接无法打开")).toBeOnTheScreen();
  }
  await act(() => send("ready", {}));
  open.mockRestore();
});
it("demotes only complete copies on image errors, reports other rendering errors, and leaves saving placeholders alone", async () => {
  const f = await createFixture();
  f.reader.reportImageFailure.mockRejectedValueOnce(new Error("disk"));
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  await act(() => send("renderError", { message: "IMAGE_DECODE_FAILED:key" }));
  expect(f.reader.reportImageFailure).toHaveBeenCalledWith("1", "key", "hash");
  expect(screen.getByText("图片未完整保存，请联网后刷新。")).toBeOnTheScreen();
  await act(() => send("renderError", { message: "READER_LOAD_FAILED" }));
  expect(screen.getByText("正文显示遇到问题，可尝试刷新。")).toBeOnTheScreen();
  await act(() =>
    f.publish({ postId: "1", copyState: "ONLINE_ONLY", checkState: "CURRENT", content, lastConfirmedAt: null }),
  );
  await act(() => send("renderError", { message: "IMAGE_UNAVAILABLE:unlisted" }));
  expect(f.reader.reportImageFailure).toHaveBeenCalledTimes(1);
  expect(screen.getByText("正文可读，正在自动保存")).toBeOnTheScreen();
});
it("keeps the existing copy during check failure and can report a refresh or local image loading failure", async () => {
  const f = await createFixture();
  f.reader.assetDataUris.mockRejectedValue(new Error("missing"));
  f.reader.refresh.mockRejectedValueOnce(new Error("刷新暂时失败"));
  f.setInitial({ postId: "1", copyState: "OFFLINE_READY", checkState: "FAILED", content, lastConfirmedAt: null });
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("正文可阅读，图片暂未完整保存。");
  expect(screen.getByText("暂未确认更新")).toBeOnTheScreen();
  await fireEvent.press(screen.getByRole("button", { name: "刷新" }));
  await screen.findByText("刷新暂时失败");
  await act(() =>
    f.publish({ postId: "1", copyState: "OFFLINE_READY", checkState: "CHECKING", content, lastConfirmedAt: null }),
  );
  expect(screen.getByText("正在确认更新")).toBeOnTheScreen();
  expect(screen.getByRole("button", { name: "刷新" })).toBeDisabled();
});
it("closes withdrawn content and keeps a readable local history explanation", async () => {
  const f = await createFixture();
  f.setInitial({ postId: "1", copyState: "UNAVAILABLE", checkState: "CURRENT", content: null, lastConfirmedAt: null });
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  expect(await screen.findByText("文章已不可访问")).toBeOnTheScreen();
  expect(screen.getByText("本机历史仍保留。")).toBeOnTheScreen();
});
it("allows explicit retries when local history or first article retrieval fails", async () => {
  const f = await createFixture();
  f.reader.listRecent.mockRejectedValueOnce(new Error("本机读取失败"));
  const result = await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("本机读取失败");
  await fireEvent.press(screen.getByRole("button", { name: "重新打开" }));
  await screen.findByText("技术长文");
  await result.unmount();
  f.setInitial({ postId: "1", copyState: "NONE", checkState: "FAILED", content: null, lastConfirmedAt: null });
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("此文尚未保存，需要联网后打开。");
  f.setInitial({ postId: "1", copyState: "NONE", checkState: "CHECKING", content: null, lastConfirmedAt: null });
  await fireEvent.press(screen.getByRole("button", { name: "重新打开" }));
  expect(await screen.findByLabelText("正在加载")).toBeOnTheScreen();
});
it("uses server column navigation rather than inferring article ID order", async () => {
  const f = await createFixture();
  const series = {
    id: "5",
    name: "系统设计",
    slug: "systems",
    posts: [],
    position: 2,
    total: 3,
    progressPercent: 66,
    previous: { id: "8", slug: "previous", title: "上一篇", seriesOrder: 1, estimatedReadingMinutes: 1 },
    next: { id: "2", slug: "next", title: "下一篇", seriesOrder: 3, estimatedReadingMinutes: 1 },
  };
  f.setInitial({
    postId: "1",
    copyState: "OFFLINE_READY",
    checkState: "CURRENT",
    content: { ...content, series },
    lastConfirmedAt: null,
  });
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  await fireEvent.press(screen.getByRole("button", { name: "系统设计 · 2/3" }));
  expect(navigation.navigate).toHaveBeenCalledWith("Column", { columnId: "5" });
  await fireEvent.press(screen.getByRole("button", { name: "上一篇" }));
  await fireEvent.press(screen.getByRole("button", { name: "下一篇" }));
  expect(navigation.replace).toHaveBeenCalledWith("Reader", { postId: "8" });
  expect(navigation.replace).toHaveBeenCalledWith("Reader", { postId: "2" });
  await act(() =>
    f.publish({
      postId: "1",
      copyState: "OFFLINE_READY",
      checkState: "CURRENT",
      content: { ...content, series: { ...series, previous: null, next: null } },
      lastConfirmedAt: null,
    }),
  );
  expect(screen.queryByRole("button", { name: "上一篇" })).toBeNull();
});
it("does not navigate from a delayed internal-link resolution after leaving the article", async () => {
  const f = await createFixture();
  const base = f.request.getMockImplementation()!;
  let resolve!: (value: any) => void;
  f.request.mockImplementation(async (path: string) =>
    path.includes("/resolve")
      ? new Promise((r) => {
          resolve = r;
        })
      : base(path),
  );
  const result = await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  navigation.push.mockClear();
  await act(() => send("link", { href: "/posts/delayed" }));
  await result.unmount();
  await act(() => resolve({ postId: "9" }));
  expect(navigation.push).not.toHaveBeenCalled();
});
it.each(["resolve", "reject"] as const)("ignores a late local-history %s after leaving the reader", async (method) => {
  const f = await createFixture();
  let finish!: (value: any) => void;
  f.reader.listRecent.mockImplementation(
    () =>
      new Promise((resolve, reject) => {
        finish = method === "resolve" ? resolve : reject;
      }),
  );
  const result = await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await result.unmount();
  await act(() => finish(method === "resolve" ? [] : new Error("late")));
  expect(f.reader.release).toHaveBeenCalledWith("1");
});
it("drops a repository snapshot whose iterator ignores cancellation", async () => {
  const f = await createFixture();
  let ready!: () => void;
  const delay = new Promise<void>((resolve) => {
    ready = resolve;
  });
  f.runtime.reader.open = async function* () {
    await delay;
    yield { postId: "1", copyState: "OFFLINE_READY", checkState: "CURRENT", content, lastConfirmedAt: null };
  };
  const result = await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await result.unmount();
  await act(() => ready());
  expect(f.reader.release).toHaveBeenCalledWith("1");
});
it.each(["resolve", "reject"] as const)(
  "ignores a late local-image %s after the web reader is destroyed",
  async (method) => {
    const f = await createFixture();
    let finish!: (value: any) => void;
    f.reader.assetDataUris.mockImplementation(
      () =>
        new Promise((resolve, reject) => {
          finish = method === "resolve" ? resolve : reject;
        }),
    );
    const result = await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
      wrapper: f.provider,
    });
    await screen.findByText("技术长文");
    await result.unmount();
    await act(() => finish(method === "resolve" ? {} : new Error("late")));
  },
);
it.each(["refresh", "link", "image"] as const)(
  "ignores a delayed %s failure after the page is destroyed or its private generation changes",
  async (operation) => {
    for (const leave of [true, false]) {
      const f = await createFixture();
      let reject!: (error: Error) => void;
      const pending = new Promise<never>((_resolve, r) => {
        reject = r;
      });
      if (operation === "refresh") f.reader.refresh.mockReturnValueOnce(pending);
      if (operation === "image") f.reader.reportImageFailure.mockReturnValueOnce(pending);
      if (operation === "link") {
        const base = f.request.getMockImplementation()!;
        f.request.mockImplementation((path: string, options?: any) =>
          path.includes("/resolve") ? pending : base(path, options),
        );
      }
      const result = await render(
        <ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />,
        { wrapper: f.provider },
      );
      await screen.findByText("技术长文");
      if (operation === "refresh") await fireEvent.press(screen.getByRole("button", { name: "刷新" }));
      else
        await act(() =>
          send(
            operation === "link" ? "link" : "renderError",
            operation === "link" ? { href: "/posts/late" } : { message: "IMAGE_DECODE_FAILED:key" },
          ),
        );
      if (leave) await result.unmount();
      else await act(() => f.auth.clearPrivateState("expired"));
      await act(() => reject(new Error("失效页面的错误")));
      if (!leave) {
        expect(screen.queryByText("失效页面的错误")).toBeNull();
        expect(screen.queryByText("图片未完整保存，请联网后刷新。")).toBeNull();
        await result.unmount();
      }
    }
  },
);
const oldPosition = {
  postId: "1",
  contentVersion: 1,
  projectionVersion: 1,
  segmentId: "p",
  codePointOffset: 1,
  blockKind: "prose",
  blockText: "原文字",
  precedingHeadings: ["第一节"],
  viewportRatio: 0.2,
  progress: 0.5,
  updatedAt: "2026-10-22T00:00:00Z",
};
const oldDocument = {
  projectionVersion: 1,
  digest: "digest",
  text: "第一节\n原文字",
  segments: [
    { id: "h", blockId: "heading", kind: "prose", text: "第一节", start: 0, end: 3 },
    { id: "p", blockId: "paragraph", kind: "prose", text: "原文字", start: 4, end: 7 },
  ],
};
it.each(["heading", "exact"] as const)(
  "uses the available old document for a %s resume across a confirmed refresh",
  async (kind) => {
    const f = await createFixture();
    const oldContent = { ...content, document: oldDocument };
    f.reader.listRecent.mockResolvedValue([{ postId: "1", position: oldPosition }]);
    f.setInitial({
      postId: "1",
      copyState: "OFFLINE_READY",
      checkState: "CURRENT",
      content: oldContent,
      lastConfirmedAt: null,
    });
    await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
      wrapper: f.provider,
    });
    await screen.findByText("技术长文");
    expect(mockReader.resume).toEqual(oldPosition);
    expect(mockReader.previousDocument).toEqual(oldDocument);
    const text = kind === "exact" ? "原文字" : "新文字";
    const next = {
      ...oldContent,
      contentVersion: 2,
      representationHash: "hash2",
      digest: "d2",
      document: {
        ...oldDocument,
        digest: "d2",
        text: `第一节\n${text}`,
        segments: [oldDocument.segments[0], { ...oldDocument.segments[1], id: "p2", text }],
      },
    };
    await act(() =>
      f.publish({
        postId: "1",
        copyState: "OFFLINE_READY",
        checkState: "CURRENT",
        content: next,
        lastConfirmedAt: null,
      }),
    );
    if (kind === "heading") expect(screen.getByText("正文已更新，已返回相邻章节")).toBeOnTheScreen();
    else expect(screen.queryByText("正文已更新，已返回相邻章节")).toBeNull();
  },
);
it("explains why an old position cannot be confirmed when the old document no longer exists", async () => {
  const f = await createFixture();
  f.reader.listRecent.mockResolvedValue([{ postId: "1", position: oldPosition }]);
  f.setInitial({
    postId: "1",
    copyState: "OFFLINE_READY",
    checkState: "CURRENT",
    content: { ...content, contentVersion: 2, representationHash: "hash2" },
    lastConfirmedAt: null,
  });
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  expect(await screen.findByText("正文已更新，无法确认原位置，已返回篇首。")).toBeOnTheScreen();
});
it("reports a position write failure while keeping the readable body visible", async () => {
  const f = await createFixture();
  f.reader.savePosition.mockRejectedValueOnce(new Error("位置暂未保存"));
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  await act(() => send("position", oldPosition));
  expect(screen.getByText("位置暂未保存")).toBeOnTheScreen();
  expect(screen.getByText("技术长文")).toBeOnTheScreen();
});
it("keeps public reading available when annotations fail to load and shows the failure only in the panel", async () => {
  const f = await createFixture();
  f.request.mockRejectedValue(new Error("评注暂不可读"));
  await render(<ReaderScreen route={{ params: { postId: "1" } }} navigation={navigation as never} />, {
    wrapper: f.provider,
  });
  await screen.findByText("技术长文");
  expect(screen.queryByText("评注暂不可读")).toBeNull();
  await fireEvent.press(screen.getByRole("button", { name: "评注" }));
  expect(await screen.findByText("评注暂不可读")).toBeOnTheScreen();
});
