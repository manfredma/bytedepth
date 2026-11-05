// 仅持久化尝试时间；身份、OIDC 临时材料及 Token 均留在服务端。
export function createAuthRecovery(browser, renderedAuthenticated) {
  const { document, navigator, localStorage, location } = browser;
  const key = "bytedepth.auth.last-attempt";
  return async function check() {
    if (!navigator.locks) {
      return;
    }
    try {
      await navigator.locks.request("bytedepth.auth.recovery", {}, async (lock) => {
        if (!lock) {
          return;
        }
        const controller = new browser.AbortController();
        const timeout = browser.setTimeout(() => controller.abort(), 20_000);
        const request = async (url, options = {}) => {
          const response = await browser.fetch(url, {
            credentials: "same-origin",
            cache: "no-store",
            signal: controller.signal,
            ...options,
          });
          if (!response.ok) {
            throw new Error("Authentication unavailable");
          }
          return response;
        };
        try {
          const status = await (await request("/auth/status")).json();
          if (status.authenticated) {
            if (!renderedAuthenticated) {
              location.reload();
            }
            return;
          }
          if (status.suppressed || status.interactive) {
            return;
          }
          const last = Number(localStorage.getItem(key));
          const now = Date.now();
          if (last > 0 && now - last < 300_000) {
            return;
          }
          // 写入必须早于 POST 和跨站授权；整个尝试持锁，其他标签页不能抢先启动。
          localStorage.setItem(key, String(now));
          const prepared = await (await request("/auth/status?prepare=true")).json();
          const response = await request("/auth/silent", {
            method: "POST",
            headers: { "X-CSRF-TOKEN": prepared.csrfToken },
          });
          if (response.status === 204) {
            return;
          }
          const result = await response.json();
          if (result.start !== "/oauth2/authorization/hearth?silent=true") {
            return;
          }
          if (controller.signal.aborted) {
            return;
          }
          const frame = document.createElement("iframe");
          frame.hidden = true;
          frame.title = "后台认证";
          const outcome = await new Promise((resolve) => {
            const finish = (value) => {
              browser.removeEventListener("message", onMessage);
              controller.signal.removeEventListener("abort", onAbort);
              frame.remove();
              resolve(value);
            };
            const onAbort = () => finish("anonymous");
            const onMessage = (event) => {
              if (event.origin !== location.origin || event.source !== frame.contentWindow) {
                return;
              }
              if (event.data?.type !== "bytedepth-auth") {
                return;
              }
              if (!["success", "anonymous", "pending", "blocked"].includes(event.data.outcome)) {
                return;
              }
              finish(event.data.outcome);
            };
            browser.addEventListener("message", onMessage);
            controller.signal.addEventListener("abort", onAbort, { once: true });
            frame.src = result.start;
            document.body.append(frame);
          });
          if (outcome === "success") {
            const verified = await (await request("/auth/status")).json();
            if (verified.authenticated) {
              location.reload();
            }
          }
        } finally {
          browser.clearTimeout(timeout);
        }
      });
    } catch {
      // 无存储、断网、无回调及协议失败都保留阅读页，不切换为交互式登录。
    }
  };
}

const script = document.querySelector("script[data-auth-recovery]");
if (script) {
  const check = createAuthRecovery(window, script.dataset.authenticated === "true");
  void check();
  window.addEventListener("pageshow", check);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) {
      void check();
    }
  });
}
