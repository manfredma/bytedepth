import React from "react";
import { Platform } from "react-native";
import * as Native from "react-native";
import { render, screen, fireEvent, userEvent } from "@testing-library/react-native";
import { Button, ArticleRow, Page, Label, Row, Sheet } from "../../components/ui";
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
