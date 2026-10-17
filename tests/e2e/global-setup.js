import { chromium, expect } from "@playwright/test";
import { authenticationCookies } from "./helpers/admin-auth.js";

// One real credential login per complete run, across both device projects and
// replacement workers. Never write a storageState file or log the run session.
export default async function globalSetup(config) {
  delete process.env.E2E_ADMIN_COOKIES;
  const username = process.env.E2E_ADMIN_USERNAME;
  const password = process.env.E2E_ADMIN_PASSWORD;
  if (!username || !password) throw new Error("Staging E2E requires the configured admin identity");
  const { baseURL, launchOptions } = config.projects[0].use;
  const browser = await chromium.launch(launchOptions);
  try {
    const context = await browser.newContext({ baseURL });
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
    // Report rate limits and authentication failures at their source, once.
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
