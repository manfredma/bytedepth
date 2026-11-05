import { defineConfig, devices } from "@playwright/test";
import { verificationHeaders } from "./tests/e2e/helpers/verification-traffic.js";

// 只由 Host 在恢复真实 staging 应用后执行；敏感认证流不产生浏览器附件。
const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
const resolver = process.env.E2E_LOCAL_HOST_RESOLVER_RULE;
const launchOptions = {
  executablePath,
  ignoreDefaultArgs: ["--hide-scrollbars"],
  ...(resolver ? { args: [`--host-resolver-rules=${resolver}`] } : {}),
};
export default defineConfig({
  testDir: "./tests/oidc",
  workers: 1,
  retries: 0,
  timeout: 120_000,
  forbidOnly: true,
  use: {
    baseURL: "https://staging-bytedepth.bytedepth.cn",
    extraHTTPHeaders: verificationHeaders,
    launchOptions,
    trace: "off",
    screenshot: "off",
    video: "off",
  },
  projects: [
    { name: "oidc-desktop", use: { ...devices["Desktop Chrome"] } },
    { name: "oidc-mobile", use: { ...devices["Pixel 5"] } },
  ],
});
