import { newVerificationContext } from "./helpers/verification-traffic.js";
import { loginAsAdmin } from "./helpers/admin-auth.js";
import { expect, test } from "@playwright/test";

async function createDraft(page, title, content) {
  await page.goto("/admin/posts/new", { waitUntil: "commit" });
  const categoryId = await page.locator('select[name="categoryId"] option').first().getAttribute("value");
  const created = await page.evaluate(
    async ({ postTitle, postContent, category }) => {
      const csrf = document.querySelector('meta[name="_csrf"]')?.content;
      const body = new URLSearchParams({ title: postTitle, content: postContent, categoryId: category, _csrf: csrf });
      const response = await fetch("/admin/posts", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body,
      });
      return { ok: response.ok, status: response.status };
    },
    { postTitle: title, postContent: content, category: categoryId },
  );
  expect(created.ok, `draft creation failed with HTTP ${created.status}`).toBeTruthy();

  await page.goto(`/admin/posts?title=${encodeURIComponent(title)}`, { waitUntil: "commit" });
  const card = page.locator(".post-card").filter({ hasText: title }).first();
  await expect(card).toBeVisible();
  return {
    editPath: new URL(await card.locator(".btn-edit").getAttribute("href"), page.url()).pathname,
    postPath: new URL(await card.locator(".title").getAttribute("href"), page.url()).pathname,
  };
}

async function deleteDraft(page, editPath) {
  await page.goto(editPath, { waitUntil: "commit" });
  const csrf = await page.locator('meta[name="_csrf"]').getAttribute("content");
  const deleted = await page.evaluate(
    async ({ path, token }) => {
      const response = await fetch(path.replace(/\/edit$/, "/delete"), {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ _csrf: token }),
      });
      return { ok: response.ok, status: response.status };
    },
    { path: editPath, token: csrf },
  );
  expect(deleted.ok, `draft cleanup failed with HTTP ${deleted.status}`).toBeTruthy();
}

test("文章远处图片在滚动后加载，首图和图片预览保持可用", async ({ page }, testInfo) => {
  expect(process.env.E2E_ADMIN_USERNAME, "staging admin credentials required").toBeTruthy();
  expect(process.env.E2E_ADMIN_PASSWORD, "staging admin credentials required").toBeTruthy();
  await loginAsAdmin(page);
  const run = `${Date.now()}-${testInfo.project.name}`;
  const leading = `/icons/logo.svg?leading=${run}`;
  const distant = `/icons/logo.svg?distant=${run}`;
  const content = [
    `![首图](${leading} "width=300")`,
    ...Array.from({ length: 100 }, (_, i) => `第 ${i + 1} 段。阅读内容与批注正文保持原有顺序。`),
    `![远处图](${distant} "width=700")`,
  ].join("\n\n");
  const paths = await createDraft(page, `image-loading-e2e-${run}`, content);
  const distantRequests = [];
  page.on("request", (request) => {
    if (request.url().includes(`distant=${run}`)) distantRequests.push(request.url());
  });
  try {
    await page.goto(paths.postPath, { waitUntil: "load" });
    const first = page.locator('.content img[alt="首图"]');
    const last = page.locator('.content img[alt="远处图"]');
    await expect(first).toHaveAttribute("loading", "eager");
    await expect(first).toHaveAttribute("width", "300");
    await expect(last).toHaveAttribute("loading", "lazy");
    await expect(last).toHaveAttribute("width", "700");
    await expect.poll(() => first.evaluate((image) => image.complete && image.naturalWidth > 0)).toBe(true);
    expect(distantRequests).toEqual([]);
    expect(await last.evaluate((image) => image.getBoundingClientRect().top)).toBeGreaterThan(
      page.viewportSize().height,
    );
    await last.scrollIntoViewIfNeeded();
    await expect.poll(() => last.evaluate((image) => image.complete && image.naturalWidth > 0)).toBe(true);
    expect(distantRequests.length).toBeGreaterThan(0);
    await last.click();
    const dialog = page.locator(".bd-image-lightbox");
    await expect(dialog).toBeVisible();
    await expect(dialog.locator("img")).toHaveAttribute("src", new RegExp(`distant=${run}`));
    await dialog.dispatchEvent("wheel", { deltaY: -100, ctrlKey: true });
    await expect(dialog).toHaveClass(/bd-image-lightbox--zoomed/);
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();
    await expect(last).toBeFocused();
    await page.screenshot({ path: testInfo.outputPath("loaded-image.png") });
  } finally {
    await deleteDraft(page, paths.editPath);
  }
});

test.describe("脚本延迟下的导航初始化", () => {
  // Service Worker fetch handlers bypass page.route; this case needs a real paused request.
  test.use({ serviceWorkers: "block" });

  test("文章延迟脚本尚未完成时手机导航已收起且可以操作", async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== "mobile-chromium", "mobile initial layout");
    await page.goto("/");
    expect(await page.evaluate(() => navigator.serviceWorker.controller)).toBeNull();
    const article = await page.locator(".bd-home-article h3 a").first().getAttribute("href");
    expect(article).toBeTruthy();
    await page.addInitScript(() => {
      window.articleDomReady = false;
      document.addEventListener(
        "DOMContentLoaded",
        () => {
          window.articleDomReady = true;
        },
        { once: true },
      );
    });
    let release;
    const gate = new Promise((resolve) => {
      release = resolve;
    });
    let requested = false;
    await page.route("**/vendor/prism/*.js", async (route) => {
      requested = true;
      await gate;
      await route.continue();
    });
    try {
      await page.goto(article, { waitUntil: "commit" });
      await expect.poll(() => requested, { message: "Prism 请求必须进入拦截，才能验证脚本加载前的导航" }).toBe(true);
      const toggle = page.locator(".nav-toggle");
      await expect(page.locator(".content")).toBeAttached();
      expect(await page.evaluate(() => window.articleDomReady)).toBe(false);
      await expect(page.locator(".nav-panel")).toBeHidden();
      await toggle.click();
      await expect(page.locator(".nav-panel")).toBeVisible();
      await page.keyboard.press("Escape");
      await expect(page.locator(".nav-panel")).toBeHidden();
      await expect(toggle).toBeFocused();
      await page.screenshot({ path: testInfo.outputPath("navigation-before-dom-ready.png") });
    } finally {
      release();
      await page.unrouteAll({ behavior: "wait" });
    }
  });
});

test("禁用脚本仍能访问导航入口", async ({ browser }, testInfo) => {
  const context = await newVerificationContext(browser, {
    javaScriptEnabled: false,
    viewport: { width: 390, height: 844 },
  });
  const page = await context.newPage();
  try {
    await page.goto(
      testInfo.project.use.baseURL || process.env.E2E_BASE_URL || "https://staging-bytedepth.bytedepth.cn",
    );
    await expect(page.locator('.nav-primary a[href="/posts"]')).toBeVisible();
    await expect(page.locator('.nav-primary a[href="/columns"]')).toBeVisible();
    await page.locator('.nav-primary a[href="/posts"]').click();
    await expect(page).toHaveURL(/\/posts$/);
  } finally {
    await context.close();
  }
});
