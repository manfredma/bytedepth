import { expect, test } from "@playwright/test";
import { loginAsAdmin, createArticle, deleteArticle } from "./helpers/annotation-fixture.js";
import { submitOwnedForm } from "./helpers/owned-form.js";

test.use({ extraHTTPHeaders: {} });

test("无痕开关、验证链接和退出保持阅读统计边界", async ({ page, context }, testInfo) => {
  await loginAsAdmin(page);
  const article = await createArticle(page, `privacy-${Date.now()}-${testInfo.project.name}`, "无痕阅读测试正文。");
  try {
    await page.goto(article.editPath, { waitUntil: "domcontentloaded" });
    await submitOwnedForm(page, article.editPath.replace(/\/edit$/, "/publish"), {});
    await page.goto(article.postPath, { waitUntil: "domcontentloaded" });
    const path = new URL(page.url()).pathname;
    await expect(page.locator("#post-reading-tracker")).toHaveAttribute("data-record-reading", "true");
    const pv = Number(await page.locator(".post-view-count span").textContent());
    const history = await page.evaluate(() => localStorage.getItem("bytedepth.reading-history.v1"));
    expect(JSON.parse(history)[path.split("/").pop()].readCount).toBe(1);
    await page.goto("/reading-history", { waitUntil: "domcontentloaded" });
    const preservedHistory = await page.evaluate(() => localStorage.getItem("bytedepth.reading-history.v1"));
    const toggle = page.getByRole("switch", { name: "无痕阅读" });
    await expect(toggle).toHaveAttribute("aria-checked", "false");
    await toggle.click();
    await expect(toggle).toHaveAttribute("aria-checked", "true");
    const cookie = (await context.cookies()).find((item) => item.name === "bd_reading_mode");
    expect(cookie.value).toBe("private");
    expect(cookie.expires).toBe(-1);
    await page.goto(path, { waitUntil: "domcontentloaded" });
    await expect(page.locator("#post-reading-tracker")).toHaveAttribute("data-record-reading", "false");
    expect(Number(await page.locator(".post-view-count span").textContent())).toBe(pv);
    expect(await page.evaluate(() => localStorage.getItem("bytedepth.reading-history.v1"))).toBe(preservedHistory);
    await page.goto("/reading-history", { waitUntil: "domcontentloaded" });
    await toggle.click();
    await expect(toggle).toHaveAttribute("aria-checked", "false");
    await page.goto(`${path}?bd_traffic=verification`, { waitUntil: "domcontentloaded" });
    await expect(page.locator("#post-reading-tracker")).toHaveAttribute("data-record-reading", "false");
    const verification = (await context.cookies()).find((item) => item.name === "bd_reading_mode");
    expect(verification.value).toBe("verification");
    expect(verification.expires - Date.now() / 1000).toBeGreaterThan(1750);
    expect(verification.expires - Date.now() / 1000).toBeLessThanOrEqual(1800);
    await page.goto(path, { waitUntil: "domcontentloaded" });
    expect(Number(await page.locator(".post-view-count span").textContent())).toBe(pv);
    await page.goto(`${path}?bd_traffic=normal`, { waitUntil: "domcontentloaded" });
    await expect(page.locator("#post-reading-tracker")).toHaveAttribute("data-record-reading", "true");
    expect(Number(await page.locator(".post-view-count span").textContent())).toBe(pv + 1);
    expect((await context.cookies()).some((item) => item.name === "bd_reading_mode")).toBe(false);
    await page.goto(`${path}?bd_traffic=verification`, { waitUntil: "domcontentloaded" });
    await context.addCookies([{ ...verification, expires: Date.now() / 1000 + 1 }]);
    expect((await context.cookies()).some((item) => item.name === "bd_reading_mode")).toBe(true);
    await expect
      .poll(async () => (await context.cookies()).some((item) => item.name === "bd_reading_mode"))
      .toBe(false);
    await page.goto(path, { waitUntil: "domcontentloaded" });
    await expect(page.locator("#post-reading-tracker")).toHaveAttribute("data-record-reading", "true");
    expect(Number(await page.locator(".post-view-count span").textContent())).toBe(pv + 2);
  } finally {
    await deleteArticle(page, article);
  }
});

test("匿名读者通过清楚的视觉与文字反馈开启和关闭无痕阅读", async ({ page }) => {
  await page.goto("/reading-history", { waitUntil: "domcontentloaded" });
  const toggle = page.getByRole("switch", { name: "无痕阅读" });
  const panel = page.getByRole("region", { name: "无痕阅读" });
  const description = page.locator("#reading-privacy-description");
  const presentation = () =>
    toggle.evaluate((button) => {
      const thumb = button.querySelector(".reading-history-privacy-thumb").getBoundingClientRect();
      const track = button.querySelector(".reading-history-privacy-track");
      const bounds = button.getBoundingClientRect();
      return {
        position: thumb.left - track.getBoundingClientRect().left,
        background: getComputedStyle(track).backgroundColor,
        width: bounds.width,
        height: bounds.height,
      };
    });
  await expect(page.locator("#reading-history-page")).toHaveAttribute("data-authenticated", "false");
  const offDescription = await description.textContent();
  const off = await presentation();
  expect(off.width).toBeGreaterThanOrEqual(44);
  expect(off.height).toBeGreaterThanOrEqual(44);
  await toggle.focus();
  await page.keyboard.press("Space");
  await expect(toggle).toHaveAttribute("aria-checked", "true");
  await expect(panel.getByText("已开启", { exact: true })).toBeVisible();
  await expect(description).not.toHaveText(offDescription);
  const on = await presentation();
  expect(on.position - off.position).toBeGreaterThanOrEqual(18);
  expect(on.background).not.toBe(off.background);
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 });
    for (const theme of ["default", "paper", "blue", "green", "midnight", "rose"]) {
      await page.evaluate((value) => document.documentElement.setAttribute("data-theme", value), theme);
      await expect(toggle).toBeInViewport();
      await expect(panel).toBeInViewport();
      const geometry = await panel.evaluate((element) => ({
        left: element.getBoundingClientRect().left,
        right: element.getBoundingClientRect().right,
        width: window.innerWidth,
        overflow: document.documentElement.scrollWidth > window.innerWidth,
      }));
      expect(geometry.left).toBeGreaterThanOrEqual(0);
      expect(geometry.right).toBeLessThanOrEqual(geometry.width);
      expect(geometry.overflow).toBe(false);
    }
  }
  await toggle.click();
  await expect(toggle).toHaveAttribute("aria-checked", "false");
  await expect(panel.getByText("未开启", { exact: true })).toBeVisible();
  await expect(description).toHaveText(offDescription);
  expect((await presentation()).position).toBe(off.position);
});
