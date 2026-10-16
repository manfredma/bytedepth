import { expect } from "@playwright/test";
import { test, createAnnotation, ready, updateArticle } from "./helpers/annotation-fixture.js";

test("正文变更后存活范围精确迁移，失效原引用和评论保留", async ({ page, article }) => {
  const old = "第一段\n\n目标句子\n\n旧方案\n\n待删除内容";
  await updateArticle(article.adminPage, article, old);
  await ready(page, article.postPath);
  const live = await createAnnotation(page, "目标句子", "存活评论");
  const replaced = await createAnnotation(page, "旧方案", "原方案评论");
  const deleted = await createAnnotation(page, "待删除内容");
  const originalAnchor = live.anchor;
  await updateArticle(article.adminPage, article, "新增前言\n\n第一段\n\n目标句子\n\n新方案");
  await ready(page, article.postPath);
  await expect(page.locator(`mark[data-id="${live.id}"]`)).toHaveText("目标句子");
  await expect(page.locator(`mark[data-id="${replaced.id}"],mark[data-id="${deleted.id}"]`)).toHaveCount(0);
  await page.locator("#bd-annotation-sidebar-toggle").click();
  await expect(page.locator(`.bd-annotation-feed-item[data-id="${live.id}"]`)).toContainText("存活评论");
  const unresolved = page.locator(`.bd-annotation-unresolved-comments [data-id="${replaced.id}"]`);
  await expect(unresolved.locator("blockquote")).toHaveText("旧方案");
  await expect(unresolved).toContainText("原方案评论");
  const highlights = page.locator(".bd-annotation-unresolved-highlights");
  await expect(highlights).not.toHaveAttribute("open");
  await highlights.locator("summary").click();
  await expect(highlights.locator(`[data-id="${deleted.id}"] blockquote`)).toHaveText("待删除内容");
  const rows = await page.evaluate(async () => (await fetch(`${location.pathname}/annotations`)).json());
  expect(rows.find((row) => row.id === live.id).anchor).toEqual(originalAnchor);
  expect(rows.find((row) => row.id === replaced.id)).toMatchObject({
    selectedText: "旧方案",
    annotationText: "原方案评论",
    placement: { status: "UNRESOLVED" },
  });
});

test("删除被批注的重复段落不会跳到另一处同文", async ({ page, article }) => {
  await updateArticle(article.adminPage, article, "同样原句\n\n中间段落\n\n同样原句");
  await ready(page, article.postPath);
  const original = await createAnnotation(page, "同样原句", "第二处的原评论", "PUBLIC", 1);
  await updateArticle(article.adminPage, article, "同样原句\n\n中间段落");
  await ready(page, article.postPath);
  await expect(page.locator(`mark[data-id="${original.id}"]`)).toHaveCount(0);
  await page.locator("#bd-annotation-sidebar-toggle").click();
  await expect(page.locator(`.bd-annotation-unresolved-comments [data-id="${original.id}"]`)).toContainText(
    "第二处的原评论",
  );
});
