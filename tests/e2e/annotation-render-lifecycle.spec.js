import { newVerificationContext } from "./helpers/verification-traffic.js";
import { expect } from "@playwright/test";
import {
  test,
  createAnnotation,
  createArticle,
  deleteArticle,
  ready,
  selectQuote,
  updateArticle,
} from "./helpers/annotation-fixture.js";

const body =
  "前文保持精确\n\n```java\nint anchorProbe = 1;\n```\n\n图前正文\n\n```mermaid\ngraph LR\nA[起点] --> B[终点]\n```\n\n图后正文\n\n```mermaid\nthis is not a diagram\n```\n\n失败图后正文";

async function rows(page, path) {
  return page.evaluate(async (path) => {
    const response = await fetch(`${path}/annotations`);
    if (!response.ok) throw new Error(`list failed: ${response.status}`);
    return response.json();
  }, path);
}

async function attempt(page, path, method, payload) {
  return page.evaluate(
    async ({ path, method, payload }) => {
      const csrf = document.querySelector('meta[name="_csrf"]').content;
      const response = await fetch(path, {
        method,
        headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf },
        body: payload ? JSON.stringify(payload) : undefined,
      });
      return { status: response.status, text: await response.text() };
    },
    { path, method, payload },
  );
}

test("真实 Prism 与 Mermaid 成功和失败后代码及图前后批注均精确保留", async ({ page, article }, testInfo) => {
  test.setTimeout(60000);
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await updateArticle(article.adminPage, article, body);
  await ready(page, article.postPath);
  await expect(page.locator(".mermaid svg")).toHaveCount(1);
  await expect(page.locator(".bd-mermaid-error")).toHaveCount(1);
  await selectQuote(page, "anchorProbe");
  const popup = page.locator(".bd-annotation-popup");
  await popup.getByRole("button", { name: "划线", exact: true }).click();
  const response = page.waitForResponse((r) => r.url().endsWith("/annotations") && r.request().method() === "POST");
  await popup.getByRole("button", { name: "蓝色双线" }).click();
  const savedResponse = await response;
  expect(savedResponse.status()).toBe(200);
  const code = await savedResponse.json();
  const before = await createAnnotation(page, "图前正文", "图前评论");
  const after = await createAnnotation(page, "图后正文", "图后评论");
  const failedAfter = await createAnnotation(page, "失败图后正文", "失败不会丢评论");
  await page.reload({ waitUntil: "commit" });
  await ready(page);
  await expect(page.locator(".mermaid svg")).toHaveCount(1);
  await expect(page.locator(".bd-mermaid-error")).toHaveCount(1);
  const svg = await page.locator(".mermaid svg").boundingBox();
  expect(svg.width).toBeGreaterThan(0);
  expect(svg.height).toBeGreaterThan(0);
  for (const annotation of [code, before, after, failedAfter])
    await expect(page.locator(`mark[data-id="${annotation.id}"]`)).toHaveText(annotation.selectedText);
  await page.evaluate(() => {
    for (let i = 0; i < 3; i++) window.Prism.highlightAllUnder(document.querySelector("#post-article .content"));
    window.initPostMermaid();
  });
  await expect(page.locator(`mark[data-id="${code.id}"]`)).toHaveText("anchorProbe");
  await expect(page.locator(".mermaid svg")).toHaveCount(1);
  const diagramSelection = await page.evaluate(() => {
    const root = document.querySelector("#post-article .content"),
      label = root.querySelector("svg .nodeLabel");
    const range = document.createRange();
    range.selectNodeContents(label);
    const map = window.AnnotationDocument.create(root, window.__ANNOTATION_DOCUMENT__);
    const result = map.describe(range);
    map.dispose();
    return result;
  });
  expect(diagramSelection).toEqual({ error: "INVALID_MAPPING" });
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
  await page
    .locator(".bd-code-block")
    .filter({ has: page.locator("code.language-java") })
    .locator(".bd-code-block__copy")
    .click();
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe("int anchorProbe = 1;\n");
  await page
    .locator(".bd-code-block")
    .filter({ has: page.locator("code.language-mermaid") })
    .first()
    .locator(".bd-code-block__copy")
    .click();
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe("graph LR\nA[起点] --> B[终点]\n");
  await expect(page.locator(`mark[data-id="${code.id}"]`)).toHaveText("anchorProbe");
  if (testInfo.project.name === "mobile-chromium") {
    await page.locator(`mark[data-id="${after.id}"]`).click();
    await expect(page.locator(".bd-annotation-mobile-note")).toHaveText("图后评论");
  } else {
    await page.locator("#bd-annotation-sidebar-toggle").click();
    await expect(page.locator(`.bd-annotation-feed-item[data-id="${after.id}"]`)).toContainText("图后评论");
    const outline = page.locator(`.bd-annotation-comment-outline[data-annotation-id="${after.id}"]`);
    await expect(outline).toBeVisible();
    await expect
      .poll(() => outline.evaluate((el) => Math.abs(el.getBoundingClientRect().top + 9 - Number(el.dataset.textTop))))
      .toBeLessThan(1);
  }
  await testInfo.attach("real-renderer-annotations", {
    body: await page.screenshot({ fullPage: true }),
    contentType: "image/png",
  });
  expect(errors).toEqual([]);
});

test("旧页面提交返回409并保留同一次操作的选区或评论草稿", async ({ page, article }, testInfo) => {
  await ready(page, article.postPath);
  await selectQuote(page, "批注测试正文");
  const popup = page.locator(".bd-annotation-popup");
  const mobile = testInfo.project.name === "mobile-chromium";
  if (mobile) await popup.getByRole("button", { name: "划线", exact: true }).click();
  else {
    await popup.getByRole("button", { name: "评论", exact: true }).click();
    await page.locator(".bd-annotation-composer-text").fill("必须保留的草稿");
  }
  await updateArticle(
    article.adminPage,
    article,
    "新增前言\n\n批注测试正文包含足够字符用于选区和布局验证。\n\n第二段独立内容。",
  );
  const button = mobile
    ? popup.getByRole("button", { name: "琥珀色波浪线" })
    : page.locator(".bd-annotation-composer-save");
  const bodies = [];
  for (let i = 0; i < 2; i++) {
    const response = page.waitForResponse((r) => r.url().endsWith("/annotations") && r.request().method() === "POST");
    await button.click();
    const result = await response;
    expect(result.status()).toBe(409);
    bodies.push(result.request().postData());
    await expect(page.locator(mobile ? ".bd-annotation-copy-result" : ".bd-annotation-composer-save")).toHaveAttribute(
      "data-status",
      "409",
    );
    if (!mobile) await expect(page.locator(".bd-annotation-composer-text")).toHaveValue("必须保留的草稿");
  }
  expect(bodies[1]).toBe(bodies[0]);
  expect((await rows(page, article.postPath)).filter((r) => r.ownedByCurrentVisitor)).toEqual([]);
});

test("匿名身份与登录身份对已定位及未定位私有记录保持隔离，草稿无泄露", async ({ page, browser, baseURL, article }) => {
  test.setTimeout(60000);
  await ready(page, article.postPath);
  // Exercise the installed worker, rather than accidentally passing before it controls the page.
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
    if (!navigator.serviceWorker.controller)
      await new Promise((resolve) =>
        navigator.serviceWorker.addEventListener("controllerchange", resolve, { once: true }),
      );
  });
  const privateA = await createAnnotation(page, "批注测试正文", "匿名A私有", "PRIVATE");
  const publicA = await createAnnotation(page, "第二段独立内容", "匿名A公开", "PUBLIC");
  const secondContext = await newVerificationContext(browser, { baseURL });
  article.contexts.add(secondContext);
  const second = await secondContext.newPage();
  article.readers.add(second);
  await ready(second, article.postPath);
  const privateB = await createAnnotation(second, "批注测试正文", "匿名B私有", "PRIVATE");
  await ready(article.adminPage, article.postPath);
  const privateUser = await createAnnotation(article.adminPage, "批注测试正文", "登录身份私有", "PRIVATE");
  for (const updated of [false, true]) {
    if (updated) await updateArticle(article.adminPage, article, "整段删除后保留评论");
    for (const reader of [page, second, article.adminPage]) {
      await ready(reader, article.postPath);
      if (updated) await expect(reader.locator("#post-article .content")).toHaveText("整段删除后保留评论");
    }
    const a = await rows(page, article.postPath),
      b = await rows(second, article.postPath),
      user = await rows(article.adminPage, article.postPath);
    expect(a.map((r) => r.id).sort()).toEqual([privateA.id, publicA.id].sort());
    expect(b.map((r) => r.id).sort()).toEqual([privateB.id, publicA.id].sort());
    expect(user.map((r) => r.id).sort()).toEqual([privateUser.id, publicA.id].sort());
    const revision = await page.evaluate(() => window.__ANNOTATION_DOCUMENT__.revision);
    for (const views of [a, b, user])
      for (const view of views)
        expect(
          view.placement.targetRevision,
          JSON.stringify({ id: view.id, placement: view.placement, pageRevision: revision }),
        ).toBe(revision);
    for (const reader of [page, second, article.adminPage]) {
      const cachedPrivatePaths = await reader.evaluate(async () => {
        const paths = [];
        for (const name of await caches.keys()) {
          if (!name.startsWith("bytedepth-")) continue;
          const cache = await caches.open(name);
          for (const request of await cache.keys()) {
            const path = new URL(request.url).pathname;
            if (path.startsWith("/posts/") || path.startsWith("/admin")) paths.push(path);
          }
        }
        return paths;
      });
      expect(cachedPrivatePaths).toEqual([]);
    }
    expect(a.find((r) => r.id === privateA.id).placement.status).toBe(updated ? "UNRESOLVED" : "ANCHORED");
    expect(b.find((r) => r.id === publicA.id).ownedByCurrentVisitor).toBe(false);
    for (const result of [...a, ...b, ...user]) {
      expect(result).not.toHaveProperty("ownerTokenHash");
      expect(result).not.toHaveProperty("userId");
      expect(result).not.toHaveProperty("ownerKey");
    }
    expect(
      (
        await attempt(second, `${article.postPath}/annotations/${publicA.id}`, "PATCH", {
          annotationText: "越权修改",
          visibility: "PUBLIC",
        })
      ).status,
    ).toBe(400);
    expect((await attempt(second, `${article.postPath}/annotations/${publicA.id}`, "DELETE")).status).toBe(400);
    expect((await rows(page, article.postPath)).find((r) => r.id === publicA.id).annotationText).toBe("匿名A公开");
  }
  const draft = await createArticle(article.adminPage, `批注隐私草稿-${Date.now()}`, "draft-secret-body");
  try {
    for (const path of [draft.postPath, `${draft.postPath}/annotations`]) {
      const response = await second.request.get(path);
      expect(response.status()).toBe(404);
      expect(await response.text()).not.toContain("draft-secret-body");
    }
    await ready(article.adminPage, draft.postPath);
    expect(
      (
        await attempt(article.adminPage, `${draft.postPath}/annotations/${publicA.id}`, "PATCH", {
          annotationText: "跨文章修改",
          visibility: "PUBLIC",
        })
      ).status,
    ).toBe(400);
    expect((await attempt(article.adminPage, `${draft.postPath}/annotations/${publicA.id}`, "DELETE")).status).toBe(
      400,
    );
    expect((await rows(page, article.postPath)).find((row) => row.id === publicA.id).annotationText).toBe("匿名A公开");
  } finally {
    await deleteArticle(article.adminPage, draft);
  }
});
