import { expect, test } from "@playwright/test";

test("匿名阅读历史为空时显示空态且不请求文章元数据", async ({ page }) => {
  await page.addInitScript(() => localStorage.removeItem("bytedepth.reading-history.v1"));
  const metadataRequests = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname === "/reading-history/available-posts") {
      metadataRequests.push(request.url());
    }
  });
  const response = await page.goto("/reading-history", { waitUntil: "domcontentloaded" });
  expect(response.ok()).toBe(true);
  await expect(page.locator("#reading-history-page")).toHaveAttribute("data-authenticated", "false");
  await expect(page.locator(".reading-history-empty")).toBeVisible();
  await expect(page.locator(".reading-history-list")).toBeEmpty();
  expect(metadataRequests).toEqual([]);
});
