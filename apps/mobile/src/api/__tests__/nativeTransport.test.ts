import { fetch } from "expo/fetch";
import { nativeTransport } from "../nativeTransport";
jest.mock("expo/fetch", () => ({ fetch: jest.fn() }));
it("bounds native response consumption even when server does not provide Content-Length", async () => {
  let cancelled = false;
  jest.mocked(fetch).mockResolvedValue({
    status: 200,
    headers: new Headers({ "Content-Type": "application/json" }),
    body: {
      getReader: () => ({
        read: async () => ({ done: false, value: new Uint8Array(16 * 1024 * 1024 + 1) }),
        cancel: async () => {
          cancelled = true;
        },
      }),
    },
  } as unknown as Awaited<ReturnType<typeof fetch>>);
  const response = await nativeTransport("https://staging-bytedepth.bytedepth.cn/api/mobile/v1/posts", {
    method: "GET",
    headers: { Accept: "application/json" },
    redirect: "error",
    credentials: "omit",
  });
  await expect(response.text()).rejects.toMatchObject({ code: "RESOURCE_TOO_LARGE" });
  expect(cancelled).toBe(true);
});
it("decodes valid bounded UTF-8 native response", async () => {
  let done = false;
  jest.mocked(fetch).mockResolvedValue({
    status: 200,
    headers: new Headers({ "Content-Type": "application/json" }),
    body: {
      getReader: () => ({
        read: async () => (done ? { done: true } : { done: false, value: ((done = true), new Uint8Array([123, 125])) }),
        cancel: async () => undefined,
      }),
    },
  } as unknown as Awaited<ReturnType<typeof fetch>>);
  const response = await nativeTransport("https://staging-bytedepth.bytedepth.cn/api/mobile/v1/posts", {
    method: "GET",
    headers: {},
    redirect: "error",
    credentials: "omit",
  });
  expect(await response.text()).toBe("{}");
});
