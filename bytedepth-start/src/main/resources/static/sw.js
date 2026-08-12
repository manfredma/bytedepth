// bytedepth Service Worker
// 策略：内容指纹静态资源 cache-first，页面导航 network-first + 离线回退。
// CSS / JS 的 URL 由 Spring 依据内容生成 hash；内容变更时自然使用新缓存键。

const CACHE_NAME = 'bytedepth-v5';

// 预缓存的核心资源
const PRECACHE_URLS = [
  '/',
  '/posts',
  '/columns',
  '/projects',
  '/icons/logo.svg',
  '/icons/favicon.svg',
  '/icons/favicon-48.png',
  '/icons/favicon-192.png',
  '/icons/favicon-512.png',
  '/favicon.ico',
  '/offline.html'
];

// ── Install：预缓存核心资源 ──────────────────────────────
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

// ── Activate：清理旧版本缓存 ─────────────────────────────
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

// ── Fetch：请求拦截策略 ───────────────────────────────────
self.addEventListener('fetch', event => {
  const { request } = event;
  const url = new URL(request.url);

  // 只处理同源请求
  if (url.origin !== location.origin) return;

  // Cache API 只支持 GET；批注创建、编辑和删除等写请求必须直达网络。
  if (request.method !== 'GET') return;

  // 管理后台和搜索不走缓存（实时性要求高）
  if (url.pathname.startsWith('/admin')) return;
  if (request.mode === 'navigate') {
    // 页面导航：network-first，断网回退离线页
    event.respondWith(
      fetch(request)
        .then(response => {
          if (response.ok) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
          }
          return response;
        })
        .catch(() =>
          caches.match(request)
            .then(cached => cached || caches.match('/offline.html'))
        )
    );
  } else {
    // 静态资源：内容指纹 URL cache-first，缓存未命中则请求并写入缓存
    event.respondWith(
      caches.match(request).then(cached => {
        if (cached) return cached;
        return fetch(request).then(response => {
          if (response.ok && response.type === 'basic') {
            const clone = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
          }
          return response;
        });
      })
    );
  }
});
