import { newVerificationContext } from "./helpers/verification-traffic.js";
import { chromium, expect } from "@playwright/test";
import { authenticationCookies } from "./helpers/admin-auth.js";

// 每次完整运行仅执行一次真实凭据登录，供两个设备项目及
// 替换 Worker 共用；不写入 storageState 文件，也不记录运行会话。
export default async function globalSetup(config) {
  delete process.env.E2E_ADMIN_COOKIES;
  const username = process.env.E2E_ADMIN_USERNAME;
  const password = process.env.E2E_ADMIN_PASSWORD;
  if (!username || !password) throw new Error("Staging E2E requires the configured admin identity");
  const { baseURL, launchOptions } = config.projects[0].use;
  const browser = await chromium.launch(launchOptions);
  try {
    const context = await newVerificationContext(browser, { baseURL });
    const page = await context.newPage();
    await page.goto("/login", { waitUntil: "domcontentloaded" });
    await page.locator('input[name="username"]').fill(username);
    await page.locator('input[name="password"]').fill(password);
    const [response] = await Promise.all([
      page.waitForResponse(
        (response) => response.request().method() === "POST" && new URL(response.url()).pathname === "/login",
      ),
      page.getByRole("button", { name: "登录", exact: true }).click(),
    ]);
    // 在源头报告限流和认证失败，且仅报告一次。
    expect(response.status(), "Admin login must succeed without retrying rate limits").toBe(302);
    await page.goto("/admin/posts/new", { waitUntil: "domcontentloaded" });
    await expect(page.locator("form.post-editor-form")).toBeVisible();
    process.env.E2E_ADMIN_COOKIES = JSON.stringify(authenticationCookies(await context.storageState()));
  } finally {
    await browser.close();
  }
  return () => {
    delete process.env.E2E_ADMIN_COOKIES;
  };
}
