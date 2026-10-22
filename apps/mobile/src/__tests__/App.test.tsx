import React from "react";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react-native";
import App from "../../App";
import { createFixture } from "../screens/__tests__/fixtures";
import { createNativeRuntime } from "../runtime/nativeRuntime";
jest.mock(
  "react-native-safe-area-context",
  () => jest.requireActual("react-native-safe-area-context/jest/mock").default,
);
jest.mock("react-native-webview", () => {
  const React = jest.requireActual("react");
  return {
    WebView: React.forwardRef(function NativeWebView(_props: unknown, ref: unknown) {
      React.useImperativeHandle(ref, () => ({ injectJavaScript: () => {} }));
      return null;
    }),
  };
});
jest.mock("../runtime/nativeRuntime", () => ({ createNativeRuntime: jest.fn() }));
it("renders the four reading entrances and opens a guest article", async () => {
  const f = await createFixture();
  await render(<App createRuntime={async () => f.runtime} />);
  expect(await screen.findByText("随时继续读，慢慢理解。")).toBeOnTheScreen();
  for (const name of ["发现", "专栏", "最近", "我"]) expect(screen.getByText(name)).toBeOnTheScreen();
  await fireEvent.press(await screen.findByText("技术长文"));
  expect(await screen.findByRole("button", { name: "目录" })).toBeOnTheScreen();
});
it("shows a retryable startup failure and disposes native resources when unmounted", async () => {
  const f = await createFixture();
  const factory = jest.fn().mockRejectedValueOnce(new Error("storage")).mockResolvedValueOnce(f.runtime);
  const result = await render(<App createRuntime={factory} />);
  expect(await screen.findByText("本机阅读数据暂时无法打开，请重试。")).toBeOnTheScreen();
  await fireEvent.press(screen.getByText("重试"));
  await screen.findByText("随时继续读，慢慢理解。");
  await result.unmount();
  expect(f.runtime.dispose).toHaveBeenCalledTimes(1);
});
it("disposes a late initialization result after the app was destroyed", async () => {
  const f = await createFixture();
  let resolve!: (value: typeof f.runtime) => void;
  const ready = new Promise<typeof f.runtime>((r) => {
    resolve = r;
  });
  const result = await render(<App createRuntime={() => ready} />);
  await result.unmount();
  resolve(f.runtime);
  await waitFor(() => expect(f.runtime.dispose).toHaveBeenCalled());
});
it("uses the native runtime factory by default and leaves a destroyed startup failure alone", async () => {
  const f = await createFixture();
  await f.runtime.preferences.set({ theme: "dark", fontScale: 1 });
  jest.mocked(createNativeRuntime).mockResolvedValueOnce(f.runtime);
  const result = await render(<App />);
  await screen.findByText("随时继续读，慢慢理解。");
  await result.unmount();
  let reject!: (reason: Error) => void;
  const pending = new Promise<never>((_resolve, r) => {
    reject = r;
  });
  const late = await render(<App createRuntime={() => pending} />);
  await late.unmount();
  reject(new Error("late storage error"));
  await Promise.resolve();
});
it("opens and resumes local recent history without logging in", async () => {
  const f = await createFixture();
  f.reader.listRecent.mockResolvedValue([
    { postId: "1", title: "上次文章", slug: "technical", copyState: "OFFLINE_READY", position: { progress: 0.4 } },
    { postId: "2", title: "需要联网文章", slug: "online", copyState: "NONE", position: null },
    { postId: "3", title: "撤回文章", slug: "removed", copyState: "UNAVAILABLE", position: null },
  ]);
  await render(<App createRuntime={async () => f.runtime} />);
  await screen.findByText("随时继续读，慢慢理解。");
  await fireEvent.press(screen.getByText("最近"));
  expect(await screen.findByText("40% · 离线可读")).toBeOnTheScreen();
  expect(screen.getByText("0% · 需要联网打开")).toBeOnTheScreen();
  await fireEvent.press(screen.getByText("上次文章"));
  expect(await screen.findByRole("button", { name: "目录" })).toBeOnTheScreen();
});
it("explains an empty history and can retry a local history read failure", async () => {
  const f = await createFixture();
  f.reader.listRecent.mockRejectedValueOnce(new Error("历史暂不可读")).mockResolvedValue([]);
  await render(<App createRuntime={async () => f.runtime} />);
  await screen.findByText("随时继续读，慢慢理解。");
  await fireEvent.press(screen.getByText("最近"));
  await screen.findByText("历史暂不可读");
  await fireEvent.press(screen.getByRole("button", { name: "重试" }));
  expect(await screen.findByText("读过的文章会自动出现在这里。")).toBeOnTheScreen();
});
it.each(["resolve", "reject"] as const)(
  "discards a delayed recent-history %s after navigation is destroyed",
  async (method) => {
    const f = await createFixture();
    let finish!: (value: any) => void;
    f.reader.listRecent.mockImplementation(
      () =>
        new Promise((resolve, reject) => {
          finish = method === "resolve" ? resolve : reject;
        }),
    );
    const result = await render(<App createRuntime={async () => f.runtime} />);
    await screen.findByText("随时继续读，慢慢理解。");
    await fireEvent.press(screen.getByText("最近"));
    await result.unmount();
    await act(() => finish(method === "resolve" ? [] : new Error("late history")));
    expect(f.runtime.dispose).toHaveBeenCalled();
  },
);
