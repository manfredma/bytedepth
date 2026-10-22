import { newVerificationContext } from "../../../../tests/e2e/helpers/verification-traffic.js";

test("new E2E contexts preserve options and always declare verification traffic", async () => {
  const received = [];
  const browser = {
    newContext: async (options) => {
      received.push(options);
      return { options };
    },
  };
  await newVerificationContext(browser);
  const context = await newVerificationContext(browser, {
    baseURL: "https://staging.example",
    javaScriptEnabled: false,
    extraHTTPHeaders: { "X-Other": "value", "X-Bytedepth-Traffic": "normal" },
  });
  expect(received[0].extraHTTPHeaders).toEqual({ "X-Bytedepth-Traffic": "verification" });
  expect(context.options).toEqual({
    baseURL: "https://staging.example",
    javaScriptEnabled: false,
    extraHTTPHeaders: { "X-Other": "value", "X-Bytedepth-Traffic": "verification" },
  });
});
