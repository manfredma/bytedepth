import { expect, test } from "@playwright/test";

const postSlug = process.env.E2E_POST_SLUG ?? "hello-bytedepth-3";

test("公共页面的无分隔英文不会撑宽手机页面和页头", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile-chromium", "仅在移动 Chromium 执行");

  await page.goto("/columns", { waitUntil: "domcontentloaded" });
  const columnHref = await page.locator(".series-link").first().getAttribute("href");
  expect(columnHref).toBeTruthy();
  const columnPath = new URL(columnHref, page.url()).pathname;

  const routes = [
    "/",
    "/posts",
    `/posts/${postSlug}`,
    "/columns",
    columnPath,
    "/search?q=java",
    "/projects",
    "/network",
    "/releases",
    "/about",
    "/u/admin",
    "/login",
    "/register",
  ];

  for (const route of routes) {
    const response = await page.goto(route, { waitUntil: "domcontentloaded" });
    expect(response?.ok(), `public route should load: ${route}`).toBeTruthy();

    const metrics = await page.evaluate(() => {
      const content = document.querySelector("#post-article .content");
      const parent = content ?? document.body;
      const probe = document.createElement("p");
      probe.dataset.longWordOverflowProbe = "true";
      probe.textContent = "unseparatedenglishletters".repeat(24);
      parent.append(probe);
      window.scrollTo({ left: 64, top: 0, behavior: "instant" });

      const result = {
        documentWidth: document.documentElement.scrollWidth,
        viewportWidth: document.documentElement.clientWidth,
        horizontalScroll: window.scrollX,
        headerLeft: document.querySelector(".nav-bar")?.getBoundingClientRect().left ?? null,
        wordWrap: getComputedStyle(probe).overflowWrap,
      };
      probe.remove();
      return result;
    });

    expect(metrics.documentWidth, `document width on ${route}`).toBeLessThanOrEqual(metrics.viewportWidth);
    expect(metrics.horizontalScroll, `horizontal scroll on ${route}`).toBe(0);
    if (metrics.headerLeft !== null) {
      expect(metrics.headerLeft, `header position on ${route}`).toBeGreaterThanOrEqual(0);
    }
    expect(metrics.wordWrap, `word wrapping on ${route}`).toBe("anywhere");
  }
});
