import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { newVerificationContext } from "../e2e/helpers/verification-traffic.js";

const business = "https://staging-bytedepth.bytedepth.cn";
const hearth = "https://staging-hearth.bytedepth.cn";
const target = "/posts?auth-check=one%2Btwo&tag=first&tag=second#reader";

function routeKind(raw) {
  const url = new URL(raw);
  if (url.origin === hearth) {
    if (url.pathname === "/login") return "hearth-login";
    if (url.pathname === "/oauth2/authorize") return "hearth-authorize";
    if (url.pathname.includes("consent")) return "hearth-consent";
    return "hearth-other";
  }
  if (url.origin !== business) return "other";
  if (url.pathname === "/login/oauth2/code/hearth") return "callback";
  if (url.pathname === "/auth/silent-complete") return "completion";
  if (url.pathname === "/auth/status") return "status";
  if (url.pathname === "/auth/silent") return "prepare";
  if (url.pathname.startsWith("/oauth2/")) return "start";
  if (url.pathname === "/login") return url.searchParams.has("error") ? "business-login-error" : "business-login";
  return "business-public";
}

function observe(context, name) {
  const started = Date.now();
  let state;
  const known = new Set([
    "login_required",
    "consent_required",
    "interaction_required",
    "authorization_request_not_found",
    "invalid_state_parameter",
    "invalid_nonce",
    "invalid_id_token",
    "invalid_token_response",
    "invalid_user_info_response",
    "server_error",
    "temporarily_unavailable",
    "protocol_error",
    "identity_unavailable",
  ]);
  const emit = (fields) =>
    console.log("OIDC_EVENT " + JSON.stringify({ context: name, elapsedMs: Date.now() - started, ...fields }));
  context.on("request", (request) => {
    const kind = routeKind(request.url());
    if (!["hearth-authorize", "callback", "prepare", "completion"].includes(kind)) return;
    const url = new URL(request.url());
    if (kind === "hearth-authorize") state = url.searchParams.get("state");
    const error = url.searchParams.get("error");
    emit({
      kind,
      event: "request",
      promptNone: url.searchParams.get("prompt") === "none",
      codePresent: url.searchParams.has("code"),
      stateMatched: kind === "callback" ? state === url.searchParams.get("state") : undefined,
      error: error ? (known.has(error) ? error : "other") : undefined,
    });
  });
  context.on("response", async (response) => {
    const kind = routeKind(response.url());
    if (!["callback", "prepare", "completion"].includes(kind)) return;
    const failure = await response.headerValue("X-Bytedepth-Auth-Failure").catch(() => null);
    const outcome = new URL(response.url()).searchParams.get("outcome");
    emit({
      kind,
      event: "response",
      status: response.status(),
      failure: failure ? (known.has(failure) ? failure : "other") : undefined,
      outcome: ["success", "anonymous", "pending", "blocked"].includes(outcome) ? outcome : undefined,
    });
  });
  context.on("requestfailed", (request) => {
    const kind = routeKind(request.url());
    if (["hearth-authorize", "callback", "completion"].includes(kind)) emit({ kind, event: "network-failure" });
  });
  return emit;
}

// 不把浏览器 API 的敏感 URL、输入值或响应体带入失败诊断。
async function privateAction(action) {
  try {
    return await action();
  } catch {
    throw new Error("Private authentication operation failed");
  }
}
async function status(context) {
  const response = await privateAction(() => context.request.get(business + "/auth/status"));
  expect(response.status()).toBe(200);
  return response.json();
}
async function login(page, navigate = true) {
  const login = process.env.E2E_ADMIN_USERNAME;
  const password = process.env.E2E_ADMIN_PASSWORD;
  expect(Boolean(login && password)).toBe(true);
  await privateAction(async () => {
    if (navigate) await page.goto(hearth + "/login", { waitUntil: "domcontentloaded" });
    await page.getByLabel("账号").fill(login);
    await page.getByLabel("密码").fill(password);
    await page.getByRole("checkbox", { name: "保持登录 30 天" }).check();
    const result = page.waitForResponse((response) => new URL(response.url()).pathname === "/api/login");
    await page.getByRole("button", { name: "登录", exact: true }).click();
    expect((await result).status()).toBe(200);
  });
  await expect
    .poll(
      () => {
        const kind = routeKind(page.url());
        return ["business-public", "business-login-error", "business-login", "hearth-consent"].includes(kind);
      },
      { timeout: 20_000 },
    )
    .toBe(true);
  const terminal = routeKind(page.url());
  console.log("OIDC_LOGIN_TERMINAL " + terminal);
  expect(terminal).toBe("business-public");
}
async function signedInNavigation(page) {
  await expect(page.locator(".nav-logout")).toHaveCount(1);
  const toggle = page.locator(".nav-toggle");
  if ((await toggle.isVisible()) && (await toggle.getAttribute("aria-expanded")) === "false") {
    await privateAction(() => toggle.click());
  }
  await expect(page.locator(".nav-logout")).toBeVisible();
}
async function visit(page) {
  await privateAction(() => page.goto(business + target, { waitUntil: "domcontentloaded" }));
}
async function quiet(page, context) {
  await expect.poll(async () => page.locator("iframe[title='后台认证']").count()).toBe(0);
  expect(new URL(page.url()).origin === business).toBe(true);
  expect(new URL(page.url()).pathname + new URL(page.url()).search + new URL(page.url()).hash === target).toBe(true);
  expect((await status(context)).authenticated).toBe(false);
  expect(await page.getByText("登录未完成", { exact: true }).count()).toBe(0);
}

// 单个场景矩阵复用一次真实 Hearth 凭据登录，降低限流干扰；所有 Cookie 只在内存中。
test("real background Auth matrix without public-page navigation", async ({ browser }, testInfo) => {
  const device = testInfo.project.use;
  const options = {
    viewport: device.viewport,
    isMobile: device.isMobile,
    hasTouch: device.hasTouch,
    userAgent: device.userAgent,
    baseURL: business,
  };
  const contexts = [];
  const context = async (name) => {
    const result = await newVerificationContext(browser, options);
    observe(result, name || "isolated-" + contexts.length);
    contexts.push(result);
    return result;
  };
  try {
    const anonymous = await context();
    const reader = await anonymous.newPage();
    let attempts = 0;
    reader.on("request", (request) => {
      const url = new URL(request.url());
      if (url.origin === hearth && url.pathname === "/oauth2/authorize") attempts++;
    });
    await visit(reader);
    await expect.poll(() => attempts).toBe(1);
    await quiet(reader, anonymous);
    await reader.reload({ waitUntil: "domcontentloaded" });
    await quiet(reader, anonymous);
    expect(attempts).toBe(1);
    console.log("OIDC_SCENARIO anonymous-and-refresh: PASS");

    for (const kind of ["invalid"]) {
      const isolated = await context();
      await isolated.addCookies([
        {
          name: "hearth-remember-me",
          value: "invalid-signature",
          url: hearth,
          httpOnly: true,
          secure: true,
          sameSite: "Lax",
          ...(kind === "browser-expired" ? { expires: Math.floor(Date.now() / 1000) - 1 } : {}),
        },
      ]);
      const page = await isolated.newPage();
      let ended = false;
      page.on("response", (response) => {
        const url = new URL(response.url());
        if (url.origin === business && url.pathname === "/auth/silent-complete") ended = true;
      });
      await visit(page);
      await expect.poll(() => ended).toBe(true);
      await quiet(page, isolated);
      console.log("OIDC_SCENARIO " + kind + ": PASS");
    }

    const authenticated = await context();
    const loginPage = await authenticated.newPage();
    let interactiveBackgroundAttempts = 0;
    authenticated.on("request", (request) => {
      const url = new URL(request.url());
      if (url.origin === hearth && url.pathname === "/oauth2/authorize" && url.searchParams.get("prompt") === "none")
        interactiveBackgroundAttempts++;
    });
    await privateAction(() => loginPage.goto(business + "/login?interactive=true"));
    await expect
      .poll(() => new URL(loginPage.url()).origin === hearth && new URL(loginPage.url()).pathname === "/login")
      .toBe(true);
    const duringInteractive = await authenticated.newPage();
    const preparation = duringInteractive.waitForResponse(
      (response) => new URL(response.url()).origin === business && new URL(response.url()).pathname === "/auth/status",
    );
    await visit(duringInteractive);
    expect((await (await privateAction(() => preparation)).json()).interactive).toBe(true);
    expect(interactiveBackgroundAttempts).toBe(0);
    await login(loginPage, false);
    await signedInNavigation(loginPage);
    expect((await status(authenticated)).authenticated).toBe(true);
    console.log("OIDC_SCENARIO interactive-login-not-overwritten-by-public-tab: PASS");
    const original = await authenticated.cookies(hearth);
    const remember = original.filter((cookie) => cookie.name === "hearth-remember-me");
    const sessions = original.filter((cookie) => cookie.name !== "hearth-remember-me");
    expect(remember.length).toBe(1);
    expect(sessions.length > 0).toBe(true);

    // 将真实签名 Remember-Me 的浏览器过期时间设为过去；浏览器移除后应匿名。
    const expired = await context();
    await expired.addCookies(remember.map((cookie) => ({ ...cookie, expires: Math.floor(Date.now() / 1000) - 1 })));
    expect((await expired.cookies(hearth)).length).toBe(0);
    const expiredPage = await expired.newPage();
    let expiredEnded = false;
    expiredPage.on("response", (response) => {
      const url = new URL(response.url());
      if (url.origin === business && url.pathname === "/auth/silent-complete") expiredEnded = true;
    });
    await visit(expiredPage);
    await expect.poll(() => expiredEnded).toBe(true);
    await quiet(expiredPage, expired);
    console.log("OIDC_SCENARIO browser-expired-signed-cookie-removed: PASS");

    for (const [name, cookies] of [
      ["remember-only", remember],
      ["session-only", sessions],
    ]) {
      const isolated = await context(name);
      await isolated.addCookies(cookies);
      expect((await isolated.cookies(business)).length).toBe(0);
      if (name === "remember-only") {
        expect((await isolated.cookies(hearth)).every((cookie) => cookie.name === "hearth-remember-me")).toBe(true);
      }
      const page = await isolated.newPage();
      let attempts = 0;
      let firstHearthPath;
      const observeSilentAuthorization = (request) => {
        const url = new URL(request.url());
        if (url.origin === hearth) {
          firstHearthPath ??= url.pathname;
          if (url.pathname === "/oauth2/authorize") {
            attempts++;
            expect(url.searchParams.get("prompt") === "none").toBe(true);
            expect(url.searchParams.get("code_challenge_method") === "S256").toBe(true);
          }
        }
      };
      page.on("request", observeSilentAuthorization);
      await visit(page);
      // 先等待浏览器完成认证；并发 API status 会创建/轮换共享 Cookie，不能充当被动观察器。
      await expect(page.locator(".nav-logout")).toHaveCount(1, { timeout: 25_000 });
      expect((await status(isolated)).authenticated).toBe(true);
      expect(firstHearthPath === "/oauth2/authorize").toBe(true);
      expect(new URL(page.url()).pathname + new URL(page.url()).search + new URL(page.url()).hash === target).toBe(
        true,
      );
      await page.reload({ waitUntil: "domcontentloaded" });
      expect(attempts).toBe(1);
      console.log("OIDC_SCENARIO " + name + "-real-callback-and-original-url: PASS");

      if (name === "remember-only") {
        const oldTab = await isolated.newPage();
        await visit(oldTab);
        await signedInNavigation(page);
        await privateAction(() => page.locator(".nav-logout button").click());
        await expect.poll(() => new URL(page.url()).origin === business).toBe(true);
        expect((await status(isolated)).authenticated).toBe(false);
        expect((await status(isolated)).suppressed).toBe(true);
        await visit(oldTab);
        expect((await status(isolated)).authenticated).toBe(false);
        expect(attempts).toBe(1);
        console.log("OIDC_SCENARIO explicit-logout-and-old-tab: PASS");
        page.off("request", observeSilentAuthorization);
        await privateAction(() => page.goto(business + "/login?interactive=true"));
        await expect
          .poll(() => new URL(page.url()).origin === hearth && new URL(page.url()).pathname === "/login")
          .toBe(true);
        await visit(oldTab);
        expect((await status(isolated)).authenticated).toBe(false);
        await login(page, false);
        await signedInNavigation(page);
        expect((await status(isolated)).authenticated).toBe(true);
        expect((await status(isolated)).suppressed).toBe(false);
        console.log("OIDC_SCENARIO explicit-login-after-logout-with-concurrent-public-tab: PASS");
      }
    }

    const parallel = await context();
    await parallel.addCookies(remember);
    const one = await parallel.newPage();
    const two = await parallel.newPage();
    let parallelAttempts = 0;
    parallel.on("request", (request) => {
      const url = new URL(request.url());
      if (url.origin === hearth && url.pathname === "/oauth2/authorize") parallelAttempts++;
    });
    await Promise.all([visit(one), visit(two)]);
    await Promise.all([
      expect(one.locator(".nav-logout")).toHaveCount(1, { timeout: 25_000 }),
      expect(two.locator(".nav-logout")).toHaveCount(1, { timeout: 25_000 }),
    ]);
    expect((await status(parallel)).authenticated).toBe(true);
    await signedInNavigation(one);
    await signedInNavigation(two);
    expect(parallelAttempts).toBe(1);
    console.log("OIDC_SCENARIO parallel-tabs: PASS");

    // 独立登记的临时客户端没有既有 consent，回调仍使用真实 ByteDepth 地址。
    const csrfResponse = await privateAction(() => authenticated.request.get(hearth + "/api/csrf"));
    const csrfToken = (await csrfResponse.json()).token;
    const headers = { "X-CSRF-TOKEN": csrfToken };
    const created = await privateAction(() =>
      authenticated.request.post(hearth + "/api/admin/oauth-clients", {
        headers,
        data: {
          applicationKey: "bytedepth-e2e-" + randomUUID(),
          displayName: "ByteDepth isolated consent check",
          redirectUris: [business + "/login/oauth2/code/hearth"],
          postLogoutRedirectUris: [business + "/login"],
          scopes: ["openid", "profile", "email"],
        },
      }),
    );
    expect(created.status()).toBe(201);
    const fixtureClient = (await created.json()).clientId;
    try {
      const consent = await context();
      await consent.addCookies(sessions);
      await consent.route(hearth + "/oauth2/authorize?**", async (route) => {
        const url = new URL(route.request().url());
        url.searchParams.set("client_id", fixtureClient);
        await privateAction(() => route.continue({ url: url.toString() }));
      });
      const page = await consent.newPage();
      let consentRequired = false;
      page.on("request", (request) => {
        const url = new URL(request.url());
        if (url.origin === business && url.pathname === "/login/oauth2/code/hearth") {
          consentRequired = url.searchParams.get("error") === "consent_required";
        }
      });
      await visit(page);
      await expect.poll(() => consentRequired).toBe(true);
      await quiet(page, consent);
      console.log("OIDC_SCENARIO consent-required-real-callback: PASS");
    } finally {
      const deleted = await privateAction(() =>
        authenticated.request.delete(hearth + "/api/admin/oauth-clients/" + fixtureClient, { headers }),
      );
      expect(deleted.status()).toBe(204);
    }

    const offline = await context();
    await offline.route(hearth + "/**", (route) => route.abort());
    const offlinePage = await offline.newPage();
    let offlineAttempts = 0;
    offlinePage.on("request", (request) => {
      if (new URL(request.url()).origin === hearth) offlineAttempts++;
    });
    await visit(offlinePage);
    await expect.poll(() => offlineAttempts).toBe(1);
    await expect.poll(async () => offlinePage.locator("iframe").count(), { timeout: 25_000 }).toBe(0);
    await quiet(offlinePage, offline);
    console.log("OIDC_SCENARIO network-failure-public-page-retained: PASS");
  } catch {
    for (let i = 0; i < contexts.length; i++) {
      for (const page of contexts[i].pages())
        console.log("OIDC_TERMINAL " + JSON.stringify({ context: i, kind: routeKind(page.url()) }));
    }
    throw new Error("OIDC matrix failed; inspect safe OIDC_EVENT and OIDC_TERMINAL diagnostics");
  } finally {
    for (const owned of contexts) await owned.close();
  }
});
