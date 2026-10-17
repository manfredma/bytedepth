import { createAnnotation } from "./helpers/annotation-fixture.js";
import { loginAsAdmin } from "./helpers/admin-auth.js";
import { expect, test } from "@playwright/test";

const adminUsername = process.env.E2E_ADMIN_USERNAME;
const adminPassword = process.env.E2E_ADMIN_PASSWORD;

async function createDraft(page, title, content) {
  await page.goto("/admin/posts/new", { waitUntil: "commit" });
  const categoryId = await page.locator('select[name="categoryId"] option').first().getAttribute("value");
  const created = await page.evaluate(
    async ({ title: postTitle, content: postContent, category }) => {
      const csrf = document.querySelector('meta[name="_csrf"]')?.content;
      const body = new URLSearchParams({ title: postTitle, content: postContent, categoryId: category, _csrf: csrf });
      const response = await fetch("/admin/posts", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body,
      });
      return { ok: response.ok, status: response.status };
    },
    { title, content, category: categoryId },
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

async function updateDraft(page, editPath, title, content) {
  await page.goto(editPath, { waitUntil: "commit" });
  const categoryId = await page.locator('select[name="categoryId"] option:checked').getAttribute("value");
  const updatePath = await page.locator("form.post-editor-form").getAttribute("action");
  expect(updatePath).toBeTruthy();
  const updated = await page.evaluate(
    async ({ title: postTitle, content: postContent, category, path }) => {
      const csrf = document.querySelector('meta[name="_csrf"]')?.content;
      const body = new URLSearchParams({ title: postTitle, content: postContent, categoryId: category, _csrf: csrf });
      const response = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body,
      });
      return { ok: response.ok, status: response.status };
    },
    { title, content, category: categoryId, path: updatePath },
  );
  expect(updated.ok, `draft update failed with HTTP ${updated.status}`).toBeTruthy();
}

async function removeAnnotation(page, postPath, id) {
  await page.evaluate(
    async ({ path, annotationId }) => {
      const csrf = document.querySelector('meta[name="_csrf"]')?.content;
      const response = await fetch(`${path}/annotations/${annotationId}`, {
        method: "DELETE",
        headers: { "X-CSRF-TOKEN": csrf },
      });
      if (response.status !== 204) {
        throw new Error(`annotation cleanup failed with HTTP ${response.status}`);
      }
    },
    { path: postPath, annotationId: id },
  );
}

async function deleteDraft(page, editPath) {
  await page.goto(editPath, { waitUntil: "commit" });
  const deletePath = editPath.replace(/\/edit$/, "/delete");
  await page.evaluate(async (path) => {
    const csrf = document.querySelector('meta[name="_csrf"]')?.content;
    const response = await fetch(path, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ _csrf: csrf }),
    });
    if (!response.ok) {
      throw new Error(`draft cleanup failed with HTTP ${response.status}`);
    }
  }, deletePath);
}

test("普通和行内脚注可双向跳转，编辑后批注仍定位到正文", async ({ page }, testInfo) => {
  expect(adminUsername, "staging must supply a real admin account").toBeTruthy();
  expect(adminPassword, "staging must supply a real admin password").toBeTruthy();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await loginAsAdmin(page);
  const title = `footnotes-e2e-${testInfo.project.name}-${Date.now()}`;
  const lead =
    "术语[^memory]，这段正文需要保持批注位置。再次引用[^memory]。行内^[行内补充说明]。带后缀[^memory-2]。特殊标签[^a<b]。";
  const content = [
    lead,
    ...Array.from(
      { length: 15 },
      (_, index) => `正文段落 ${index + 1}：对齐约束的是起始地址，不能由它推断一次硬件访问的事务数量。`,
    ),
    "[^memory-2]: 后缀标签解释。",
    "[^a<b]: 特殊标签解释。",
    "[^memory]: 内存事务是一次硬件读写请求。**补充解释**。",
    "",
    "    第二段说明。<script>window.footnoteUnsafe = true</script>",
    "",
    "    [危险链接](javascript:alert(1))",
  ].join("\n\n");
  const paths = await createDraft(page, title, content);
  let annotationId;
  try {
    const response = await page.goto(paths.postPath, { waitUntil: "domcontentloaded" });
    expect(response.ok()).toBeTruthy();
    await expect(page.locator('#post-article[data-bd-annotation-ready="true"]')).toBeVisible({ timeout: 15_000 });
    const article = page.locator("#post-article .content");
    const references = article.locator(".footnote-ref > a");
    const notes = article.locator(".footnotes > ol > li");
    await expect(references).toHaveText(["1", "1", "2", "3", "4"]);
    await expect(notes).toHaveCount(4);
    await expect(notes.first().locator("p")).toHaveCount(3);
    await expect(notes.first().locator("strong")).toHaveText("补充解释");
    const ids = await article.locator("[id]").evaluateAll((elements) => elements.map((element) => element.id));
    expect(new Set(ids).size).toBe(ids.length);
    await expect(article.locator("script, a[href^='javascript:']")).toHaveCount(0);
    expect(await page.evaluate(() => window.footnoteUnsafe)).toBeUndefined();
    for (const reference of await references.all()) {
      const target = await reference.getAttribute("href");
      const referenceId = await reference.getAttribute("id");
      await reference.click();
      expect(new URL(page.url()).hash).toBe(target);
      await expect(article.locator(target)).toBeInViewport();
      const backlink = article.locator(`a.footnote-backref[href="#${referenceId}"]`);
      await expect(backlink).toHaveAccessibleName(/Back to reference/);
      await backlink.click();
      expect(new URL(page.url()).hash).toBe(`#${referenceId}`);
      await expect(reference).toBeInViewport();
    }
    await references.first().click();
    for (const theme of ["paper", "midnight", "blue"]) {
      await page.evaluate((value) => {
        document.documentElement.dataset.theme = value;
      }, theme);
      const metrics = await article.locator(".footnotes").evaluate((section) => ({
        border: getComputedStyle(section).borderTopStyle,
        fontSize: parseFloat(getComputedStyle(section).fontSize),
        viewport: document.documentElement.clientWidth,
        width: document.documentElement.scrollWidth,
      }));
      expect(metrics.border).toBe("solid");
      expect(metrics.fontSize).toBeGreaterThanOrEqual(14);
      expect(metrics.width).toBeLessThanOrEqual(metrics.viewport);
      await testInfo.attach(`footnotes-${theme}`, { body: await page.screenshot(), contentType: "image/png" });
    }
    const selectedText = "这段正文需要保持批注位置";
    const annotation = await createAnnotation(page, selectedText);
    annotationId = annotation.id;
    await updateDraft(page, paths.editPath, title, `新增导语。\n\n${content}`);
    await page.goto(paths.postPath, { waitUntil: "domcontentloaded" });
    await expect(page.locator(`mark[data-id="${annotationId}"]`).filter({ hasText: selectedText })).toBeVisible({
      timeout: 15_000,
    });
    const annotations = await page.evaluate(async () => {
      const response = await fetch(`${window.location.pathname}/annotations`);
      if (!response.ok) throw new Error(`annotations fetch failed: ${response.status}`);
      return response.json();
    });
    const saved = annotations.find((item) => item.id === annotationId);
    expect(saved.anchor).toEqual(annotation.anchor);
    expect(saved.placement.status).toBe("ANCHORED");
    const verifiedQuote = await page.evaluate((view) => {
      const map = window.AnnotationDocument.create(
        document.querySelector("#post-article .content"),
        window.__ANNOTATION_DOCUMENT__,
      );
      try {
        const ranges = map.verify(view);
        if (!Array.isArray(ranges)) throw new Error("Footnote annotation placement failed canonical verification");
        return ranges.map((range) => range.toString()).join("");
      } finally {
        map.dispose();
      }
    }, saved);
    expect(verifiedQuote).toBe(selectedText);
    expect(errors).toEqual([]);
  } finally {
    if (annotationId) await removeAnnotation(page, paths.postPath, annotationId);
    await deleteDraft(page, paths.editPath);
  }
});
