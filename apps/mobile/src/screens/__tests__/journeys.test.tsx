import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react-native";
import { DiscoveryScreen } from "../DiscoveryScreen";
import { SearchScreen } from "../SearchScreen";
import { RuntimeContext, type Runtime } from "../../runtime/Runtime";
import { Preferences } from "../../runtime/Preferences";
import { ColumnsScreen, ColumnScreen } from "../ColumnsScreen";
import { createFixture, navigation as nav } from "./fixtures";
async function setup(online = true) {
  const request = jest.fn().mockResolvedValue({
    featured: [],
    latest: [{ id: "9007199254740993", title: "技术长文", summary: "从原理开始", estimatedReadingMinutes: 8 }],
    categories: [],
    tags: [],
  });
  const preferences = await Preferences.open({ read: async () => null, write: async () => {} });
  const runtime = {
    api: { request },
    isOnline: () => online,
    subscribe: () => () => {},
    preferences,
  } as unknown as Runtime;
  const navigation = { navigate: jest.fn() };
  return { runtime, request, navigation };
}
it("lets a guest open an article using its string ID", async () => {
  const { runtime, navigation } = await setup();
  await render(
    <RuntimeContext.Provider value={runtime}>
      <DiscoveryScreen navigation={navigation as never} />
    </RuntimeContext.Provider>,
  );
  expect(await screen.findByText("技术长文")).toBeOnTheScreen();
  await fireEvent.press(screen.getByText("技术长文"));
  expect(navigation.navigate).toHaveBeenCalledWith("Reader", { postId: "9007199254740993" });
  expect(screen.queryByText("请先登录")).toBeNull();
});
it("explains that full-site search requires a connection and sends no request offline", async () => {
  const { runtime, request, navigation } = await setup(false);
  await render(
    <RuntimeContext.Provider value={runtime}>
      <SearchScreen navigation={navigation as never} />
    </RuntimeContext.Provider>,
  );
  expect(screen.getByText("搜索需要联网")).toBeOnTheScreen();
  expect(request).not.toHaveBeenCalled();
});
it("opens featured/category/tag links and explains discovery empty/offline states", async () => {
  const f = await createFixture();
  f.request.mockResolvedValue({
    featured: [{ id: "2", title: "精选文章", summary: "精选" }],
    latest: [],
    categories: [{ id: "c", name: "计算机", slug: "computer" }],
    tags: [{ id: "t", name: "Java", slug: "java" }],
  });
  await render(<DiscoveryScreen navigation={nav as never} />, { wrapper: f.provider });
  await screen.findByText("精选文章");
  await fireEvent.press(screen.getByText("精选文章"));
  await fireEvent.press(screen.getByRole("button", { name: "计算机" }));
  expect(nav.navigate).toHaveBeenCalledWith("Search", { category: "computer" });
  await fireEvent.press(screen.getByRole("button", { name: "Java" }));
  expect(nav.navigate).toHaveBeenCalledWith("Search", { tag: "java" });
  await fireEvent.press(screen.getByRole("button", { name: "搜索文章" }));
  expect(screen.getByText("暂时没有文章")).toBeOnTheScreen();
  await act(() => f.setOnline(false));
  expect(screen.getByText("已离线，可在「最近」继续阅读已保存文章。")).toBeOnTheScreen();
});
it("runs online search with encoded terms and server paging", async () => {
  const f = await createFixture();
  f.request.mockResolvedValue({ items: [{ id: "8", title: "并发原理", snippet: "搜索摘要" }], hasNext: true });
  await render(<SearchScreen navigation={nav as never} />, { wrapper: f.provider });
  await fireEvent.changeText(screen.getByLabelText("搜索关键词"), "并发 & 锁");
  await fireEvent.press(screen.getByRole("button", { name: "搜索" }));
  await screen.findByText("并发原理");
  expect(f.request).toHaveBeenCalledWith(
    "/search?query=%E5%B9%B6%E5%8F%91%20%26%20%E9%94%81&page=1",
    expect.anything(),
  );
  await fireEvent.press(screen.getByText("并发原理"));
  await fireEvent.press(screen.getByRole("button", { name: "下一页" }));
  await screen.findByRole("button", { name: "上一页" });
  await fireEvent.press(screen.getByRole("button", { name: "上一页" }));
  await screen.findByText("并发原理");
  await fireEvent(screen.getByLabelText("搜索关键词"), "submitEditing");
  expect(nav.navigate).toHaveBeenCalledWith("Reader", { postId: "8" });
});
it.each([{ category: "computer" }, { tag: "java" }])("browses the selected filter %j", async (params) => {
  const f = await createFixture();
  f.request.mockResolvedValue({ items: [{ id: "7", title: "筛选文章", summary: "摘要" }], hasNext: false });
  await render(<SearchScreen route={{ params }} navigation={nav as never} />, { wrapper: f.provider });
  await screen.findByText("筛选文章");
  expect(f.request.mock.calls[0][0]).toMatch(/\/posts\?page=1&(?:category=computer|tag=java)/);
});
it("shows no-result search and recovers from a request failure on explicit retry", async () => {
  const f = await createFixture();
  f.request.mockRejectedValueOnce(new Error("暂时离线")).mockResolvedValue({ items: [], hasNext: false });
  await render(<SearchScreen route={{ params: { tag: "java" } }} navigation={nav as never} />, { wrapper: f.provider });
  await screen.findByText("暂时离线");
  await fireEvent.press(screen.getByRole("button", { name: "重试" }));
  expect(await screen.findByText("没有找到文章，试试其他关键词。")).toBeOnTheScreen();
});
it("uses the published server order to open columns and their articles", async () => {
  const f = await createFixture();
  f.request.mockImplementation(async (path: string) =>
    path.startsWith("/columns/5")
      ? {
          name: "系统设计",
          description: "按章节学习",
          posts: { items: [{ id: "9", title: "第二章", summary: "章节摘要" }], hasNext: true },
        }
      : { items: [{ id: "5", name: "系统设计", postCount: 2, description: null }], hasNext: true },
  );
  const result = await render(<ColumnsScreen navigation={nav as never} />, { wrapper: f.provider });
  await screen.findByText("系统设计");
  await fireEvent.press(screen.getByText("系统设计"));
  expect(nav.navigate).toHaveBeenCalledWith("Column", { columnId: "5" });
  await fireEvent.press(screen.getByRole("button", { name: "下一页" }));
  await screen.findByRole("button", { name: "上一页" });
  await fireEvent.press(screen.getByRole("button", { name: "上一页" }));
  await result.unmount();
  await render(<ColumnScreen navigation={nav as never} route={{ params: { columnId: "5" } }} />, {
    wrapper: f.provider,
  });
  await screen.findByText("第二章");
  await fireEvent.press(screen.getByText("第二章"));
  expect(nav.navigate).toHaveBeenCalledWith("Reader", { postId: "9" });
  await fireEvent.press(screen.getByRole("button", { name: "下一页" }));
  await screen.findByRole("button", { name: "上一页" });
  await fireEvent.press(screen.getByRole("button", { name: "上一页" }));
});
it("explains empty columns, request errors and offline directories", async () => {
  const f = await createFixture();
  f.request.mockRejectedValueOnce(new Error("目录暂不可用")).mockResolvedValue({ items: [], hasNext: false });
  const result = await render(<ColumnsScreen navigation={nav as never} />, { wrapper: f.provider });
  await screen.findByText("目录暂不可用");
  await fireEvent.press(screen.getByRole("button", { name: "重试" }));
  await screen.findByText("暂时没有专栏");
  await act(() => f.setOnline(false));
  expect(screen.getByText("专栏目录需要联网，已保存文章可从「最近」打开。")).toBeOnTheScreen();
  await result.unmount();
  await act(() => f.setOnline(true));
  f.request
    .mockRejectedValueOnce(new Error("目录暂不可用"))
    .mockResolvedValue({ name: "空专栏", description: null, posts: { items: [], hasNext: false } });
  await render(<ColumnScreen navigation={nav as never} route={{ params: { columnId: "5" } }} />, {
    wrapper: f.provider,
  });
  await screen.findByText("目录暂不可用");
  await fireEvent.press(screen.getByRole("button", { name: "重试" }));
  await screen.findByText("暂时没有文章");
  await act(() => f.setOnline(false));
  expect(screen.getByText("专栏目录需要联网")).toBeOnTheScreen();
});
it("can explicitly retry discovery after a failure", async () => {
  const f = await createFixture();
  f.request
    .mockRejectedValueOnce(new Error("发现暂不可用"))
    .mockResolvedValue({ featured: [], latest: [], categories: [], tags: [] });
  await render(<DiscoveryScreen navigation={nav as never} />, { wrapper: f.provider });
  await screen.findByText("发现暂不可用");
  await fireEvent.press(screen.getByRole("button", { name: "重试" }));
  await screen.findByText("暂时没有文章");
});
