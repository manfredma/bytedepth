// bytedepth Service Worker
// 策略：明确的公共静态资源 cache-first，页面导航直达网络 + 中性离线页。
// CSS / JS 的 URL 由 Spring 依据内容生成 hash；内容变更时自然使用新缓存键。

const CACHE_NAME = "bytedepth-v9";

// 预缓存的核心资源
const PRECACHE_URLS = [
  "/icons/logo.svg",
  "/icons/favicon.svg",
  "/icons/favicon-48.png",
  "/icons/favicon-192.png",
  "/icons/favicon-512.png",
  "/favicon.ico",
  "/icons/favicon-staging.svg",
  "/icons/favicon-staging-48.png",
  "/icons/favicon-staging-192.png",
  "/icons/favicon-staging-512.png",
  "/favicon-staging.ico",
  "/offline.html",
];

// ── Install：预缓存核心资源 ──────────────────────────────
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting()),
  );
});

// ── Activate：清理旧版本缓存 ─────────────────────────────
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k.startsWith("bytedepth-") && k !== CACHE_NAME).map((k) => caches.delete(k))),
      )
      .then(() => self.clients.claim()),
  );
});

// ── Fetch：请求拦截策略 ───────────────────────────────────
self.addEventListener("fetch", (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // 只处理同源请求
  if (url.origin !== location.origin) return;

  // Cache API 只支持 GET；批注创建、编辑和删除等写请求必须直达网络。
  if (request.method !== "GET") return;

  // 管理后台和搜索不走缓存（实时性要求高）
  if (url.pathname.startsWith("/admin")) return;

  // CSRF token 刷新请求必须直达网络取最新页面，否则 cache-first 会返回带旧 token 的缓存 HTML。
  if (request.headers.get("X-CSRF-Refresh")) return;
  if (request.mode === "navigate") {
    // HTML 含登录状态、CSRF 和私有批注，不能进入与身份无关的 Cache API。
    // 断网只显示中性离线页，禁止回退到另一身份或旧版本的文章 HTML。
    event.respondWith(fetch(request).catch(() => caches.match("/offline.html")));
    return;
  }

  // 默认不缓存：非导航 GET 不等于静态资源。Cache API 不自动遵守 no-store，
  // 批注、搜索、阅读历史及未来新增接口必须始终直达网络。
  const fingerprintedStatic =
    /^\/(?:css|js|vendor|fonts)\//.test(url.pathname) &&
    /-[a-f0-9]{32}\.(?:css|js|woff2?|ttf|otf|svg|png|ico)$/.test(url.pathname);
  if (!fingerprintedStatic && !PRECACHE_URLS.includes(url.pathname)) return;

  // 内容指纹 URL cache-first；只有明确的公共静态响应可以写入缓存。
  event.respondWith(
    caches.match(request).then(async (cached) => {
      if (cached) return cached;
      const response = await fetch(request);
      if (response.ok && response.type === "basic") {
        const clone = response.clone();
        event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.put(request, clone)));
      }
      return response;
    }),
  );
});
