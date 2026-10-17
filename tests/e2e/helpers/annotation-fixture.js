import { expect, test as base } from "@playwright/test";
import { loginAsAdmin } from "./admin-auth.js";
import { submitOwnedForm as form } from "./owned-form.js";
export { loginAsAdmin };

export const fixtureBody = "批注测试正文包含足够字符用于选区和布局验证。\n\n第二段独立内容。";

export async function createArticle(page, title, content) {
  await page.goto("/admin/posts/new", { waitUntil: "commit" });
  const categoryId = await page.locator('select[name="categoryId"] option').first().getAttribute("value");
  await form(page, "/admin/posts", { title, content, categoryId });
  await page.goto(`/admin/posts?title=${encodeURIComponent(title)}`, { waitUntil: "commit" });
  const card = page.locator(".post-card").filter({ hasText: title });
  await expect(card).toHaveCount(1);
  const editPath = new URL(await card.locator(".btn-edit").getAttribute("href"), page.url()).pathname;
  const postPath = new URL(await card.locator(".title").getAttribute("href"), page.url()).pathname;
  return { editPath, postPath, title };
}

export { updateOwnedArticle as updateArticle } from "./owned-form.js";

export async function deleteArticle(page, article) {
  await page.goto(article.editPath, { waitUntil: "commit" });
  await form(page, article.editPath.replace(/\/edit$/, "/delete"), {});
}

export async function ready(page, path) {
  if (path) await page.goto(path, { waitUntil: "domcontentloaded" });
  await expect(page.locator('#post-article[data-bd-annotation-ready="true"]')).toBeVisible({ timeout: 15000 });
}

export async function removeAnnotation(page, path, id) {
  const result = await page.evaluate(
    async ({ path, id }) => {
      const csrf = document.querySelector('meta[name="_csrf"]')?.content;
      return (await fetch(`${path}/annotations/${id}`, { method: "DELETE", headers: { "X-CSRF-TOKEN": csrf } })).status;
    },
    { path, id },
  );
  expect(result).toBe(204);
}

export async function cleanupAnnotations(page, path) {
  await ready(page, path);
  const records = await page.evaluate(async (path) => {
    const response = await fetch(`${path}/annotations`);
    if (!response.ok) throw new Error(`annotation cleanup list failed: ${response.status}`);
    return response.json();
  }, path);
  for (const annotation of records.filter((row) => row.ownedByCurrentVisitor))
    await removeAnnotation(page, path, annotation.id);
}

// Restrict range construction to reviewed pure roots, independently of a whole-page
// textContent counter. This exercises native selection and server validation.
export async function selectQuote(page, quote, occurrence = 0) {
  await page.locator("#post-article .content").evaluate(
    (content, { quote, occurrence }) => {
      const roots = [...content.querySelectorAll("[data-annotation-segment]")].filter(
        (el) => !el.matches(".language-mermaid") && el.textContent.includes(quote),
      );
      const root = roots[occurrence];
      if (!root) throw new Error(`Missing quote ${quote}`);
      const start = root.textContent.indexOf(quote),
        end = start + quote.length;
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      let consumed = 0,
        node,
        startNode,
        endNode,
        startOffset,
        endOffset;
      while ((node = walker.nextNode())) {
        const next = consumed + node.length;
        if (!startNode && start < next) {
          startNode = node;
          startOffset = start - consumed;
        }
        if (startNode && end <= next) {
          endNode = node;
          endOffset = end - consumed;
          break;
        }
        consumed = next;
      }
      if (!startNode || !endNode) throw new Error("Incomplete native range");
      const range = document.createRange();
      range.setStart(startNode, startOffset);
      range.setEnd(endNode, endOffset);
      const selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      document.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
      content.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    },
    { quote, occurrence },
  );
}

export async function selectedAnchor(page) {
  return page.evaluate(() => {
    const root = document.querySelector("#post-article .content"),
      model = window.__ANNOTATION_DOCUMENT__;
    const map = window.AnnotationDocument.create(root, model);
    const description = map.describe(window.getSelection());
    map.dispose();
    if (description.error) throw new Error("Native selection does not match document");
    return {
      ...description,
      schemaVersion: 1,
      sourceRevision: model.revision,
      projectionVersion: model.projectionVersion,
      digest: model.digest,
      requestId: crypto.randomUUID(),
    };
  });
}

export async function createAnnotation(page, quote, note = null, visibility = "PUBLIC", occurrence = 0) {
  await selectQuote(page, quote, occurrence);
  const anchor = await selectedAnchor(page);
  return postAnnotation(page, { anchor, annotationText: note, color: "yellow", visibility });
}

export async function postAnnotation(page, payload) {
  return page.evaluate(async (payload) => {
    const csrf = document.querySelector('meta[name="_csrf"]').content;
    const response = await fetch(`${location.pathname}/annotations`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf },
      body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error(`annotation creation failed: ${response.status}`);
    return response.json();
  }, payload);
}

export const test = base.extend({
  article: async ({ browser, page, baseURL }, use, testInfo) => {
    const adminContext = await browser.newContext({ baseURL });
    const adminPage = await adminContext.newPage();
    let article;
    let published = false;
    const readers = new Set([page, adminPage]);
    const contexts = new Set([adminContext]);
    try {
      await loginAsAdmin(adminPage);
      article = await createArticle(adminPage, `批注验收-${testInfo.project.name}-${Date.now()}`, fixtureBody);
      await form(adminPage, article.editPath.replace(/\/edit$/, "/publish"), {});
      published = true;
      await use({ ...article, adminPage, readers, contexts });
    } finally {
      try {
        if (article) {
          // Only this fixture's article and the explicit reader identities are touched.
          const failures = [];
          for (const reader of published ? readers : []) {
            try {
              expect(reader.isClosed(), "Reader closed before owned annotation cleanup").toBe(false);
              await cleanupAnnotations(reader, article.postPath);
            } catch (error) {
              failures.push(error.message);
            }
          }
          try {
            await deleteArticle(adminPage, article);
          } catch (error) {
            failures.push(error.message);
          }
          expect(failures, "Owned fixture cleanup must finish for every identity").toEqual([]);
        }
      } finally {
        for (const context of contexts) await context.close();
      }
    }
  },
});
