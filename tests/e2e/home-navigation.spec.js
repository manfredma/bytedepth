import { expect, test } from "@playwright/test";

for (const width of [320, 390, 1280]) {
  test(`首页统计链接和无主推布局 ${width}px`, async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "mobile-chromium", "一次覆盖移动和桌面断点");
    await page.setViewportSize({ width, height: 844 });
    await page.goto("/", { waitUntil: "domcontentloaded" });
    const stats = page.locator(".bd-home-stats a");
    await expect(stats).toHaveCount(3);
    await expect(stats.nth(0)).toHaveAttribute("href", "/posts");
    await expect(stats.nth(1)).toHaveAttribute("href", "#home-topics");
    await expect(stats.nth(2)).toHaveAttribute("href", "/projects");
    await stats.nth(1).click();
    await expect(page).toHaveURL(/#home-topics$/);
    await expect(page.locator("#home-topics h2").first()).toBeInViewport();
    const topicsTop = await page
      .locator("#home-topics h2")
      .first()
      .evaluate((element) => element.getBoundingClientRect().top);
    const navBottom = await page.locator(".nav-bar").evaluate((element) => element.getBoundingClientRect().bottom);
    expect(topicsTop).toBeGreaterThanOrEqual(navBottom);
    await page.goto("/", { waitUntil: "domcontentloaded" });
    if (width < 1100) {
      await page.locator(".nav-toggle").click();
      await stats.nth(1).click();
      await expect(page.locator("#home-topics h2").first()).toBeInViewport();
      const expandedTopicsTop = await page
        .locator("#home-topics h2")
        .first()
        .evaluate((element) => element.getBoundingClientRect().top);
      const expandedNavBottom = await page
        .locator(".nav-bar")
        .evaluate((element) => element.getBoundingClientRect().bottom);
      expect(expandedTopicsTop).toBeGreaterThanOrEqual(expandedNavBottom);
      await page.goto("/", { waitUntil: "domcontentloaded" });
    }
    if ((await page.locator(".bd-home-feature").count()) === 0) {
      const gap = await page.locator(".bd-home-hero").evaluate((hero) => {
        const next = hero.nextElementSibling;
        return {
          feed: next.classList.contains("bd-home-recent"),
          pixels: next.getBoundingClientRect().top - hero.getBoundingClientRect().bottom,
        };
      });
      expect(gap.feed).toBe(true);
      expect(gap.pixels).toBeLessThanOrEqual(40);
      await expect(page.getByText("暂时没有主推文章，从最近更新开始阅读。")).toHaveCount(0);
    }
    if (width < 1100) {
      await page.locator(".nav-toggle").click();
    }
    await testInfo.attach(`homepage-${width}`, {
      body: await page.screenshot({ fullPage: false }),
      contentType: "image/png",
    });
    await stats.nth(0).click();
    await expect(page).toHaveURL(/\/posts$/);
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await page.locator(".bd-home-stats a").nth(2).click();
    await expect(page).toHaveURL(/\/projects$/);
  });
}
