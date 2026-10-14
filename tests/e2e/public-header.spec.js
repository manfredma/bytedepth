import { expect, test } from "@playwright/test";

async function publicRoutes(page) {
  await page.goto("/", { waitUntil: "domcontentloaded" });
  const article = await page.locator(".bd-home-article h3 a").first().getAttribute("href");
  await page.goto("/columns", { waitUntil: "domcontentloaded" });
  const column = await page.locator(".series-link").first().getAttribute("href");
  expect(article).toBeTruthy();
  expect(column).toBeTruthy();
  return [
    "/",
    "/posts",
    "/columns",
    "/projects",
    "/network",
    "/reading-history",
    "/about",
    "/releases",
    "/search?q=java",
    article,
    column,
  ];
}

for (const width of [320, 390, 768, 1280]) {
  test(`公共页头在 ${width}px 统一、贴边且控件可用`, async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "mobile-chromium", "一次覆盖手机、平板和桌面宽度");
    await page.setViewportSize({ width, height: 844 });
    const routes = await publicRoutes(page);
    let reference;
    for (const route of routes) {
      const response = await page.goto(route, { waitUntil: "domcontentloaded" });
      expect(response.ok(), route).toBeTruthy();
      const nav = page.locator(".nav-bar");
      await expect(nav).toBeVisible();
      const toggle = nav.locator(".nav-toggle");
      const panel = nav.locator(".nav-panel");
      if (width <= 1100) {
        await expect(toggle).toBeVisible();
        await expect(toggle).toHaveAttribute("aria-expanded", "false");
        await expect(toggle).toHaveText("菜单");
        await expect(panel).toBeHidden();
      } else {
        await expect(toggle).toBeHidden();
        await expect(panel).toBeVisible();
      }
      const metrics = await nav.evaluate((element) => {
        const r = element.getBoundingClientRect();
        const brand = element.querySelector(".nav-brand");
        const primary = element.querySelector(".nav-primary a");
        return {
          left: r.left,
          top: r.top,
          right: r.right,
          height: r.height,
          viewport: document.documentElement.clientWidth,
          bodyMargin: getComputedStyle(document.body).margin,
          documentWidth: document.documentElement.scrollWidth,
          brandLeft: brand.getBoundingClientRect().left,
          brandFont: getComputedStyle(brand.querySelector("strong")).fontSize,
          primaryFont: getComputedStyle(primary).fontSize,
          background: getComputedStyle(element).backgroundColor,
        };
      });
      expect(metrics.left, route).toBe(0);
      expect(metrics.top, route).toBe(0);
      expect(metrics.right, route).toBe(metrics.viewport);
      expect(metrics.bodyMargin, route).toBe("0px");
      expect(metrics.documentWidth, route).toBe(metrics.viewport);
      const appearance = {
        height: metrics.height,
        brandLeft: metrics.brandLeft,
        brandFont: metrics.brandFont,
        primaryFont: metrics.primaryFont,
        background: metrics.background,
      };
      if (!reference) reference = appearance;
      expect(appearance, `header consistency on ${route}`).toEqual(reference);
      if (width <= 1100) {
        await toggle.click();
        await expect(panel).toBeVisible();
        await expect(toggle).toHaveAttribute("aria-expanded", "true");
        await expect(toggle).toHaveText("收起");
        const layout = await nav.evaluate((element) => {
          const box = (selector) => element.querySelector(selector).getBoundingClientRect();
          return {
            searchBottom: box(".nav-search").bottom,
            primaryTop: box(".nav-primary").top,
            columns: getComputedStyle(element.querySelector(".nav-primary")).gridTemplateColumns.split(" ").length,
            themeTop: box(".theme-switcher").top,
            aboutTop: box(".nav-about-menu").top,
          };
        });
        expect(layout.searchBottom).toBeLessThanOrEqual(layout.primaryTop);
        expect(layout.columns).toBe(2);
        expect(layout.themeTop).toBe(layout.aboutTop);
      }
      const input = nav.locator('input[name="q"]');
      await expect(input).toBeVisible();
      expect((await input.boundingBox()).width, `usable search width on ${route}`).toBeGreaterThanOrEqual(160);
      const themeTrigger = nav.getByRole("button", { name: "切换主题", exact: true });
      await themeTrigger.click();
      await expect(nav.locator(".theme-menu")).toBeVisible();
      const themeOptions = await nav.locator(".theme-option").evaluateAll((options) =>
        options.map((option) => {
          const r = option.getBoundingClientRect();
          return { left: r.left, right: r.right, height: r.height, viewport: document.documentElement.clientWidth };
        }),
      );
      for (const option of themeOptions) {
        expect(option.left, `theme option left on ${route}`).toBeGreaterThanOrEqual(0);
        expect(option.right, `theme option right on ${route}`).toBeLessThanOrEqual(option.viewport);
        if (width <= 1100) expect(option.height).toBeGreaterThanOrEqual(44);
      }
      // The popup can exceed a narrow panel; its final option must remain
      // reachable through panel scrolling, rather than only the first option.
      await nav.locator('[data-theme-option="rose"]').click();
      await expect(nav.locator(".theme-menu")).toBeHidden();
      await themeTrigger.click();
      await nav.locator('[data-theme-option="default"]').click();
      await expect(nav.locator(".theme-menu")).toBeHidden();
      await nav.locator(".nav-about").click();
      await expect(nav.locator(".nav-about-menu__panel")).toBeVisible();
      await expect(nav.getByRole("link", { name: "关于本站", exact: true })).toBeVisible();
      await nav.getByRole("link", { name: "版本", exact: true }).click();
      await expect(page).toHaveURL(/\/releases$/);
      // Restore this route before measuring its navigation and homepage title.
      await page.goto(route, { waitUntil: "domcontentloaded" });
      if (width <= 1100) await toggle.click();
      const outOfBounds = await nav.evaluate((element) =>
        [...element.querySelectorAll("a,button,input,summary")]
          .filter((control) => {
            const r = control.getBoundingClientRect();
            return r.width > 0 && r.height > 0 && (r.left < 0 || r.right > document.documentElement.clientWidth);
          })
          .map((control) => control.getAttribute("aria-label") || control.textContent.trim()),
      );
      expect(outOfBounds, route).toEqual([]);
      if (route === "/" && width <= 768) {
        const title = page.locator("#home-title");
        // Collapse before measuring the first-screen content placement.
        await toggle.click();
        const collapsedNav = await nav.boundingBox();
        const titleBox = await title.boundingBox();
        expect(titleBox.x).toBe(metrics.brandLeft);
        expect(titleBox.y - collapsedNav.height).toBeLessThan(140);
      }
    }
    if (width === 320) {
      // staging normally omits RSS. Exercise the production-only control with
      // the actual staged component CSS so its layout cannot escape validation.
      await page.goto("/", { waitUntil: "domcontentloaded" });
      await page.locator(".nav-toggle").click();
      await page.locator(".nav-utility").evaluate((utility) => {
        const rss = document.createElement("a");
        rss.className = "nav-rss";
        rss.href = "/feed.xml";
        rss.setAttribute("aria-label", "订阅 RSS 更新");
        rss.textContent = "RSS";
        utility.append(rss);
      });
      expect((await page.locator(".nav-search input").boundingBox()).width).toBeGreaterThanOrEqual(160);
      const rss = await page.locator(".nav-rss").boundingBox();
      expect(rss.x).toBeGreaterThanOrEqual(0);
      expect(rss.x + rss.width).toBeLessThanOrEqual(320);
      expect(rss.height).toBeGreaterThanOrEqual(44);
    }
  });
}

test("登录后的手机页头保留账号操作、搜索和键盘关闭", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile-chromium", "移动端权限态布局");
  expect(process.env.E2E_ADMIN_USERNAME, "staging must supply admin test credentials").toBeTruthy();
  expect(process.env.E2E_ADMIN_PASSWORD, "staging must supply admin test credentials").toBeTruthy();
  await page.setViewportSize({ width: 320, height: 844 });
  await page.goto("/login", { waitUntil: "domcontentloaded" });
  await page.locator('input[name="username"]').fill(process.env.E2E_ADMIN_USERNAME);
  await page.locator('input[name="password"]').fill(process.env.E2E_ADMIN_PASSWORD);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/);
  for (const route of ["/", "/reading-history", "/posts"]) {
    await page.goto(route, { waitUntil: "domcontentloaded" });
    const nav = page.locator(".nav-bar");
    const toggle = nav.locator(".nav-toggle");
    await toggle.click();
    await expect(nav.locator(".nav-admin")).toBeVisible();
    await expect(nav.locator(".nav-write")).toBeVisible();
    const writePadding = await nav.locator(".nav-write").evaluate((element) => {
      const style = getComputedStyle(element);
      return { left: parseFloat(style.paddingLeft), right: parseFloat(style.paddingRight) };
    });
    expect(writePadding.left).toBeGreaterThanOrEqual(12);
    expect(writePadding.right).toBeGreaterThanOrEqual(12);
    await expect(nav.getByRole("button", { name: "退出", exact: true })).toBeVisible();
    const input = nav.locator('input[name="q"]');
    expect((await input.boundingBox()).width).toBeGreaterThanOrEqual(160);
    const widths = await nav.locator(".nav-auth a, .nav-auth button").evaluateAll((controls) =>
      controls.map((control) => {
        const r = control.getBoundingClientRect();
        return { left: r.left, right: r.right, viewport: document.documentElement.clientWidth, height: r.height };
      }),
    );
    for (const control of widths) {
      expect(control.left).toBeGreaterThanOrEqual(0);
      expect(control.right).toBeLessThanOrEqual(control.viewport);
      expect(control.height).toBeGreaterThanOrEqual(44);
    }
    await input.press("Escape");
    await expect(nav.locator(".nav-panel")).toBeHidden();
    await expect(toggle).toBeFocused();
    await expect(toggle).toHaveText("菜单");
  }
});
