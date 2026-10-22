import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react-native";
import { AccountScreen } from "../AccountScreen";
import { createFixture, navigation } from "./fixtures";
import { Preferences } from "../../runtime/Preferences";
import { MobileRequestError } from "../../api/errors";
it("registers an account application without pretending it is an authenticated account", async () => {
  const f = await createFixture();
  f.request.mockImplementation(async (path: string) => (path === "/registrations" ? { status: "PENDING" } : undefined));
  await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
  await fireEvent.press(screen.getByRole("button", { name: "申请账号" }));
  await fireEvent.changeText(screen.getByLabelText("用户名"), "reader");
  await fireEvent.changeText(screen.getByLabelText("密码"), "abc123456");
  await fireEvent.press(screen.getByRole("button", { name: "提交申请" }));
  expect(await screen.findByText("申请已提交，等待管理员审核后才能登录。")).toBeOnTheScreen();
  expect(f.auth.getUser()).toBeNull();
  expect(screen.getByLabelText("密码").props.value).toBe("");
  expect(screen.queryByText("清理缓存")).toBeNull();
});
it("logs in, reads own annotation references online, and removes them immediately when going offline", async () => {
  const f = await createFixture();
  const base = f.request.getMockImplementation()!;
  f.request.mockImplementation(async (path: string) =>
    path === "/me/annotations?page=1"
      ? { items: [{ id: "a", postId: "1", selectedText: "原文引用", annotationText: "本人私有文字" }], hasNext: false }
      : base(path),
  );
  await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
  await fireEvent.changeText(screen.getByLabelText("用户名"), "reader");
  await fireEvent.changeText(screen.getByLabelText("密码"), "abc123456");
  await fireEvent.press(screen.getByRole("button", { name: "登录账号" }));
  await screen.findByText("reader");
  await fireEvent.press(screen.getByRole("button", { name: "本人批注" }));
  expect(await screen.findByText("本人私有文字")).toBeOnTheScreen();
  await act(() => f.setOnline(false));
  expect(screen.queryByText("本人私有文字")).toBeNull();
  expect(screen.getByText("账号与互动功能需要联网")).toBeOnTheScreen();
});
it("changes only the local reading preferences and exposes a logout action", async () => {
  const f = await createFixture();
  await f.auth.login("reader", "password");
  await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
  const count = f.request.mock.calls.length;
  await fireEvent.press(screen.getByRole("button", { name: "深色" }));
  await fireEvent.press(screen.getByRole("button", { name: "放大字号" }));
  expect(f.runtime.preferences.snapshot()).toEqual({ theme: "dark", fontScale: 1.1 });
  expect(f.request.mock.calls.length).toBe(count);
  await fireEvent.press(screen.getByRole("button", { name: "退出登录" }));
  expect(await screen.findByText("游客阅读")).toBeOnTheScreen();
});
it("discards a delayed login error after the app loses connectivity", async () => {
  const f = await createFixture();
  let reject!: (error: Error) => void;
  f.request.mockImplementation(
    async () =>
      new Promise((_resolve, r) => {
        reject = r;
      }),
  );
  await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
  await fireEvent.changeText(screen.getByLabelText("用户名"), "reader");
  await fireEvent.changeText(screen.getByLabelText("密码"), "abc123456");
  await fireEvent.press(screen.getByRole("button", { name: "登录账号" }));
  await act(() => f.setOnline(false));
  await act(() => reject(new Error("旧账号登录结果")));
  expect(screen.queryByText("旧账号登录结果")).toBeNull();
  expect(screen.queryByLabelText("正在加载")).toBeNull();
});
it("shows registration uncertainty and login failures without retaining the password", async () => {
  const f = await createFixture();
  f.request.mockResolvedValueOnce({ status: "UNKNOWN" });
  await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
  await fireEvent.press(screen.getByRole("button", { name: "申请账号" }));
  await fireEvent.changeText(screen.getByLabelText("用户名"), "reader");
  await fireEvent.changeText(screen.getByLabelText("密码"), "abc123456");
  await fireEvent.press(screen.getByRole("button", { name: "提交申请" }));
  await screen.findByText("注册结果无法确认，请稍后重试");
  await fireEvent.press(screen.getByRole("button", { name: "登录" }));
  f.request.mockRejectedValueOnce(new Error("账号尚未审核"));
  await fireEvent.changeText(screen.getByLabelText("密码"), "abc123456");
  await fireEvent.press(screen.getByRole("button", { name: "登录账号" }));
  expect(await screen.findByText("账号尚未审核")).toBeOnTheScreen();
  expect(screen.getByLabelText("密码").props.value).toBe("");
});
it("retries own annotations, pages their references and opens the source article", async () => {
  const f = await createFixture();
  await f.auth.login("reader", "password");
  let fail = true;
  f.request.mockImplementation(async (path: string) => {
    if (fail) throw new Error("本人批注暂不可读");
    return path.endsWith("page=2")
      ? { items: [], hasNext: false }
      : { items: [{ id: "1", postId: "9", selectedText: "原文依据", annotationText: "我的思考" }], hasNext: true };
  });
  await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
  await fireEvent.press(screen.getByRole("button", { name: "本人批注" }));
  await screen.findByText("本人批注暂不可读");
  fail = false;
  await fireEvent.press(screen.getByRole("button", { name: "重试批注" }));
  await screen.findByText("我的思考");
  await fireEvent.press(screen.getByText("原文依据"));
  expect(navigation.navigate).toHaveBeenCalledWith("Reader", { postId: "9" });
  await fireEvent.press(screen.getByRole("button", { name: "下一页" }));
  await screen.findByText("还没有批注");
  await fireEvent.press(screen.getByRole("button", { name: "上一页" }));
  await screen.findByText("我的思考");
});
it("reports a failed preference write and keeps font controls within the supported bounds", async () => {
  const f = await createFixture();
  const write = jest.fn().mockRejectedValueOnce(new Error("设置暂不可保存")).mockResolvedValue(undefined);
  f.runtime.preferences = await Preferences.open({ read: async () => null, write });
  await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
  await fireEvent.press(screen.getByRole("button", { name: "浅色" }));
  await screen.findByText("设置暂不可保存");
  await fireEvent.press(screen.getByRole("button", { name: "跟随系统" }));
  for (let i = 0; i < 2; i++) await fireEvent.press(screen.getByRole("button", { name: "缩小字号" }));
  expect(screen.getByRole("button", { name: "缩小字号" })).toBeDisabled();
  for (let i = 0; i < 10; i++) await fireEvent.press(screen.getByRole("button", { name: "放大字号" }));
  expect(screen.getByRole("button", { name: "放大字号" })).toBeDisabled();
  expect(f.runtime.preferences.snapshot().fontScale).toBe(1.8);
});
it("does not display an old registration result after leaving online account state", async () => {
  const f = await createFixture();
  let resolve!: (result: any) => void;
  f.request.mockImplementation(
    async () =>
      new Promise((r) => {
        resolve = r;
      }),
  );
  await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
  await fireEvent.press(screen.getByRole("button", { name: "申请账号" }));
  await fireEvent.changeText(screen.getByLabelText("用户名"), "reader");
  await fireEvent.changeText(screen.getByLabelText("密码"), "abc123456");
  await fireEvent.press(screen.getByRole("button", { name: "提交申请" }));
  await act(() => f.setOnline(false));
  await act(() => resolve({ status: "PENDING" }));
  expect(screen.queryByText("申请已提交，等待管理员审核后才能登录。")).toBeNull();
});
it("does not revive cleared own annotations while the same account fresh request is pending", async () => {
  const f = await createFixture();
  await f.auth.login("reader", "password");
  const base = f.request.getMockImplementation()!;
  let reads = 0;
  let complete!: (value: any) => void;
  f.request.mockImplementation((path: string, options?: any) => {
    if (path.startsWith("/me/annotations")) {
      reads++;
      return reads === 1
        ? Promise.resolve({
            items: [{ id: "a", postId: "1", selectedText: "引用", annotationText: "必须清除的私有旧文字" }],
            hasNext: false,
          })
        : new Promise((resolve) => {
            complete = resolve;
          });
    }
    return base(path, options);
  });
  await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
  await fireEvent.press(screen.getByRole("button", { name: "本人批注" }));
  await screen.findByText("必须清除的私有旧文字");
  await act(() => f.setOnline(false));
  await act(() => f.setOnline(true));
  await act(() => f.auth.revalidate());
  await fireEvent.press(screen.getByRole("button", { name: "本人批注" }));
  expect(screen.queryByText("必须清除的私有旧文字")).toBeNull();
  expect(screen.getByLabelText("正在加载")).toBeOnTheScreen();
  await act(() => complete({ items: [], hasNext: false }));
  expect(screen.getByText("还没有批注")).toBeOnTheScreen();
});
it.each(["login", "register"] as const)(
  "honors the original %s Retry-After deadline across offline and account-state clearing",
  async (mode) => {
    const f = await createFixture();
    const time = jest.spyOn(Date, "now").mockReturnValue(1000);
    f.request.mockRejectedValueOnce(new MobileRequestError(429, "RATE_LIMITED", "slow", 5000));
    const base = f.request.getMockImplementation()!;
    f.request.mockImplementation((path: string, options?: any) =>
      path === "/registrations" ? Promise.resolve({ status: "PENDING" }) : base(path, options),
    );
    await render(<AccountScreen navigation={navigation as never} />, { wrapper: f.provider });
    if (mode === "register") await fireEvent.press(screen.getByRole("button", { name: "申请账号" }));
    await fireEvent.changeText(screen.getByLabelText("用户名"), "reader");
    await fireEvent.changeText(screen.getByLabelText("密码"), "abc123456");
    const submit = mode === "login" ? "登录账号" : "提交申请";
    await fireEvent.press(screen.getByRole("button", { name: submit }));
    await screen.findByText("操作较频繁，请稍后重试（约 5 秒）");
    const initial = f.request.mock.calls.length;
    time.mockReturnValue(2000);
    await act(() => f.setOnline(false));
    await act(() => f.setOnline(true));
    await act(() => f.auth.clearPrivateState("account-change"));
    await fireEvent.changeText(screen.getByLabelText("密码"), "abc123456");
    await fireEvent.press(screen.getByRole("button", { name: submit }));
    expect(f.request).toHaveBeenCalledTimes(initial);
    time.mockReturnValue(6000);
    await fireEvent.press(screen.getByRole("button", { name: submit }));
    if (mode === "register") await screen.findByText("申请已提交，等待管理员审核后才能登录。");
    else await screen.findByText("reader");
    time.mockRestore();
  },
);
