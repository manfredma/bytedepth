import { newVerificationContext } from "./helpers/verification-traffic.js";
import { expect, test } from "@playwright/test";

test("公共静态资源缓存、错误指纹拒绝和压缩", async ({ request }) => {
  const home = await request.get("/");
  expect(home.ok()).toBeTruthy();
  expect(home.headers()["cache-control"]).toContain("no-store");
  const html = await home.text();
  const theme = html.match(/href="([^" ]*\/css\/theme-[a-f0-9]{32}\.css)"/)[1];
  const css = await request.get(theme);
  expect(css.headers()["cache-control"]).toBe("max-age=31536000, public, immutable");
  expect(await css.text()).not.toContain("fonts.googleapis.com");
  const stale = await request.get(theme.replace(/-[a-f0-9]{32}/, "-00000000000000000000000000000000"));
  expect(stale.status()).toBe(404);
  expect(stale.headers()["cache-control"]).toContain("no-store");
  const worker = await request.get("/sw.js");
  expect(worker.headers()["cache-control"]).toContain("no-store");
  const icon = await request.get("/icons/logo.svg");
  expect(icon.headers()["cache-control"]).toBe("no-cache");
  const checked = await request.get("/icons/logo.svg", {
    headers: { "If-Modified-Since": icon.headers()["last-modified"] },
  });
  expect(checked.status()).toBe(304);
  // 显式指定编码，证明已部署运行时确实压缩文本，而非仅有配置。
  const compressed = await request.get(theme, { headers: { "Accept-Encoding": "gzip" } });
  expect(compressed.headers()["content-encoding"]).toBe("gzip");
});

test("没有 Service Worker 时跨页面访问也复用浏览器静态缓存", async ({ browser, baseURL }) => {
  const context = await newVerificationContext(browser, { baseURL, serviceWorkers: "block" });
  try {
    const page = await context.newPage();
    await page.goto("/", { waitUntil: "load" });
    const theme = await page.locator('link[href*="/css/theme-"]').getAttribute("href");
    await page.goto("/?sort=latest", { waitUntil: "load" });
    const timing = await page.evaluate((path) => {
      const url = new URL(path, location.href).href;
      const entry = performance.getEntriesByName(url).at(-1);
      return { transfer: entry.transferSize, decoded: entry.decodedBodySize };
    }, theme);
    expect(timing.decoded).toBeGreaterThan(0);
    expect(timing.transfer).toBe(0);
  } finally {
    await context.close();
  }
});
