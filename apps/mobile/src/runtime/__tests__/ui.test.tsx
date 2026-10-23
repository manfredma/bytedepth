import React from "react";
import { Search } from "lucide-react-native";
import fs from "node:fs";
import path from "node:path";
import { Platform } from "react-native";
import * as Native from "react-native";
import { render, screen, fireEvent, userEvent } from "@testing-library/react-native";
import { Button, ArticleRow, Page, Label, Row, Sheet, usePalette, PageHeading, ColumnCard } from "../../components/ui";
import { useRuntime } from "../Runtime";
import { createFixture } from "../../screens/__tests__/fixtures";
it("rejects a missing runtime provider", async () => {
  function Probe() {
    useRuntime();
    return null;
  }
  await expect(render(<Probe />)).rejects.toThrow("Runtime provider is required");
});
it("supports system dark display, Android keyboard layout, accessible disabled controls, and pressed feedback", async () => {
  const f = await createFixture();
  jest.spyOn(Native, "useColorScheme").mockReturnValue("dark");
  jest.replaceProperty(Platform, "OS", "android");
  const pressed = jest.fn();
  await render(
    <Page>
      <Row>
        <Button title="轻触" onPress={pressed} />
        <Button title="不可用" disabled onPress={pressed} />
      </Row>
      <ArticleRow title="仅有标题" onPress={pressed} />
      <Label heading>深色阅读</Label>
      <PageHeading title="阅读" eyebrow="READ" />
      <Button title="搜索" icon={Search} primary onPress={pressed} />
      <ColumnCard title="空说明专栏" description={null} count={0} onPress={pressed} />
      <ColumnCard title="系统设计" description="沿章节理解系统" count={12} onPress={pressed} />
    </Page>,
    { wrapper: f.provider },
  );
  await userEvent.press(screen.getByRole("button", { name: "轻触" }));
  expect(pressed).toHaveBeenCalledTimes(1);
  await fireEvent.press(screen.getByRole("button", { name: "不可用" }));
  expect(pressed).toHaveBeenCalledTimes(1);
  await fireEvent.press(screen.getByText("仅有标题"));
  expect(pressed).toHaveBeenCalledTimes(2);
  jest.restoreAllMocks();
});
it("supports an Android sheet keyboard layout", async () => {
  const f = await createFixture();
  jest.replaceProperty(Platform, "OS", "android");
  const close = jest.fn();
  await render(
    <Sheet title="阅读面板" onClose={close}>
      <Label>正文选项</Label>
    </Sheet>,
    { wrapper: f.provider },
  );
  await fireEvent.press(screen.getByRole("button", { name: "关闭" }));
  expect(close).toHaveBeenCalled();
  jest.restoreAllMocks();
});
it("bounds long article previews and gives the reading target a stable accessible name", async () => {
  const f = await createFixture();
  const title = "A long technical article";
  const detail = "Long preview paragraph. ".repeat(300);
  const open = jest.fn();
  await render(<ArticleRow title={title} detail={detail} onPress={open} />, { wrapper: f.provider });
  expect(screen.getByText(title)).toHaveProp("numberOfLines", 2);
  expect(screen.getByText(detail)).toHaveProp("numberOfLines", 3);
  await fireEvent.press(screen.getByRole("button", { name: title }));
  expect(open).toHaveBeenCalledTimes(1);
});

it.each(["light", "dark"] as const)(
  "keeps native %s colors aligned with the website semantic tokens",
  async (theme) => {
    const f = await createFixture();
    await f.runtime.preferences.set({ theme, fontScale: 1 });
    let palette!: ReturnType<typeof usePalette>;
    function Probe() {
      palette = usePalette();
      return null;
    }
    await render(<Probe />, { wrapper: f.provider });
    const css = fs.readFileSync(
      path.resolve(__dirname, "../../../../../bytedepth-start/src/main/resources/static/css/theme.css"),
      "utf8",
    );
    const block =
      theme === "light" ? css.split('[data-theme="paper"]')[0] : css.split('[data-theme="midnight"]')[1].split("}")[0];
    const tokens = {
      background: "bg",
      surface: "surface",
      surfaceMuted: "surface-muted",
      text: "text",
      muted: "text-muted",
      border: "border",
      accent: "accent",
      nav: "nav-bg",
      navText: "nav-text",
    } as const;
    for (const [native, web] of Object.entries(tokens)) {
      expect(block).toContain(`--bd-${web}: ${palette[native as keyof typeof tokens]};`);
    }
  },
);
it("keeps editorial navigation targets accessible with metadata and progress", async () => {
  const f = await createFixture();
  const open = jest.fn();
  await render(
    <ArticleRow
      title="继续阅读"
      detail="内容摘要"
      ordinal={1}
      category="计算机基础"
      meta="8 分钟阅读"
      progress={0.42}
      onPress={open}
    />,
    { wrapper: f.provider },
  );
  expect(screen.getByText("01")).toBeOnTheScreen();
  expect(screen.getByText("计算机基础")).toBeOnTheScreen();
  expect(screen.getByRole("button", { name: "继续阅读" })).toHaveAccessibilityValue({ min: 0, max: 100, now: 42 });
  await fireEvent.press(screen.getByRole("button", { name: "继续阅读" }));
  expect(open).toHaveBeenCalledTimes(1);
});
