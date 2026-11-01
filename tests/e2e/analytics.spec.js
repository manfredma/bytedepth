import { loginAsAdmin } from "./helpers/admin-auth.js";
import { expect, test } from "@playwright/test";

const adminUsername = process.env.E2E_ADMIN_USERNAME;
const adminPassword = process.env.E2E_ADMIN_PASSWORD;

test.describe("访问统计分析", () => {
  test("国家/地区流量分布接口成功并渲染饼图", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "仅在桌面 Chromium 执行");
    test.skip(!adminUsername || !adminPassword, "需要 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD 才能执行后台分析 E2E");

    await loginAsAdmin(page);
    const [response] = await Promise.all([
      page.waitForResponse(
        (candidate) =>
          candidate.url().includes("/admin/analytics/api/countries") && candidate.request().method() === "GET",
      ),
      page.goto("/admin/analytics", { waitUntil: "domcontentloaded" }),
    ]);
    expect(response.ok(), `country statistics failed with HTTP ${response.status()}`).toBeTruthy();
    await expect(page.locator("#pie-chart canvas")).toHaveCount(1);
  });
});

test.describe("访问趋势明细", () => {
  test("日与小时倒序明细可见，窄屏仅表格内部横向滚动", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "chromium", "仅在桌面 Chromium 执行");
    test.skip(!adminUsername || !adminPassword, "需要后台凭据");
    await loginAsAdmin(page);
    await page.goto("/admin/analytics", { waitUntil: "domcontentloaded" });
    const details = page.locator("#trend-details");
    await expect(details.locator("thead th")).toHaveCount(9);
    const dailyTimes = await details.locator("tbody tr td:first-child").allTextContents();
    expect(dailyTimes.length).toBeGreaterThan(0);
    expect(dailyTimes).toEqual([...dailyTimes].sort().reverse());
    await page.locator('[data-period="today"]').click();
    await expect(details.locator("tbody tr")).toHaveCount(24);
    const hours = await details.locator("tbody tr td:first-child").allTextContents();
    expect(hours[0]).toMatch(/23:00$/);
    expect(hours[23]).toMatch(/00:00$/);
    await page.setViewportSize({ width: 390, height: 844 });
    const scroll = details.locator(".analytics-trend-scroll");
    expect(await scroll.evaluate((el) => el.scrollWidth > el.clientWidth)).toBe(true);
    expect(await details.evaluate((el) => el.getBoundingClientRect().right <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath("analytics-details-mobile.png"), fullPage: true });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.screenshot({ path: testInfo.outputPath("analytics-details-desktop.png"), fullPage: true });
  });
});
