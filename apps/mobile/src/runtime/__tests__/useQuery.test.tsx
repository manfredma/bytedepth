import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react-native";
import { Text, Pressable } from "react-native";
import { useQuery, errorMessage } from "../useQuery";
import { MobileRequestError } from "../../api/errors";
import { createFixture } from "../../screens/__tests__/fixtures";
function Probe() {
  const state = useQuery<string>("/search");
  return (
    <>
      <Text>{state.error ?? state.data ?? "等待结果"}</Text>
      <Pressable accessibilityRole="button" onPress={state.reload}>
        <Text>重试</Text>
      </Pressable>
    </>
  );
}
it("honors Retry-After for explicit query retries and never starts a timer-based retry", async () => {
  const f = await createFixture();
  const time = jest.spyOn(Date, "now").mockReturnValue(1000);
  f.request
    .mockRejectedValueOnce(new MobileRequestError(429, "RATE_LIMITED", "slow", 5000))
    .mockResolvedValueOnce("结果");
  await render(<Probe />, { wrapper: f.provider });
  await screen.findByText("操作较频繁，请稍后重试（约 5 秒）");
  await fireEvent.press(screen.getByRole("button", { name: "重试" }));
  expect(f.request).toHaveBeenCalledTimes(1);
  time.mockReturnValue(6000);
  await fireEvent.press(screen.getByRole("button", { name: "重试" }));
  expect(await screen.findByText("结果")).toBeOnTheScreen();
  time.mockRestore();
});
it.each(["resolve", "reject"] as const)("drops a late %s result after leaving a query", async (method) => {
  const f = await createFixture();
  let complete!: (value: any) => void;
  f.request.mockImplementation(
    () =>
      new Promise((resolve, reject) => {
        complete = method === "resolve" ? resolve : reject;
      }),
  );
  const result = await render(<Probe />, { wrapper: f.provider });
  await result.unmount();
  await act(() => complete(method === "resolve" ? "旧结果" : new Error("旧错误")));
  expect(f.request.mock.calls[0][1].signal.aborted).toBe(true);
});
it("maps known API and unknown failures to plain reader messages", () => {
  expect(errorMessage(new MobileRequestError(429, "RATE_LIMITED", "slow"))).toBe("操作较频繁，请稍后重试");
  expect(errorMessage(new MobileRequestError(500, "INTERNAL_ERROR", "暂不可用"))).toBe("暂不可用");
  expect(errorMessage(null)).toBe("暂时无法完成，请联网后重试");
});
it("allows an explicit retry when the server supplies no Retry-After duration", async () => {
  const f = await createFixture();
  f.request.mockRejectedValueOnce(new MobileRequestError(429, "RATE_LIMITED", "slow")).mockResolvedValue("结果");
  await render(<Probe />, { wrapper: f.provider });
  await screen.findByText("操作较频繁，请稍后重试");
  await fireEvent.press(screen.getByRole("button", { name: "重试" }));
  expect(await screen.findByText("结果")).toBeOnTheScreen();
});
