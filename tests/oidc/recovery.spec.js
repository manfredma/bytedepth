import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { newVerificationContext } from "../e2e/helpers/verification-traffic.js";

const business = "https://staging-bytedepth.bytedepth.cn";
const hearth = "https://staging-hearth.bytedepth.cn";
const target = "/posts?auth-check=one%2Btwo&tag=first&tag=second#reader";

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
  await expect.poll(() => new URL(page.url()).pathname !== "/login").toBe(true);
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
  const context = async () => {
    const result = await newVerificationContext(browser, options);
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
      (response) => new URL(response.url()).origin === business && new URL(response.url()).pathname === "/auth/silent",
    );
    await visit(duringInteractive);
    expect((await privateAction(() => preparation)).status()).toBe(204);
    expect(interactiveBackgroundAttempts).toBe(0);
    await login(loginPage, false);
    await expect.poll(async () => (await status(authenticated)).authenticated).toBe(true);
    await signedInNavigation(loginPage);
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
      const isolated = await context();
      await isolated.addCookies(cookies);
      expect((await isolated.cookies(business)).length).toBe(0);
      if (name === "remember-only") {
        expect((await isolated.cookies(hearth)).every((cookie) => cookie.name === "hearth-remember-me")).toBe(true);
      }
      const page = await isolated.newPage();
      let attempts = 0;
      let firstHearthPath;
      page.on("request", (request) => {
        const url = new URL(request.url());
        if (url.origin === hearth) {
          firstHearthPath ??= url.pathname;
          if (url.pathname === "/oauth2/authorize") {
            attempts++;
            expect(url.searchParams.get("prompt") === "none").toBe(true);
            expect(url.searchParams.get("code_challenge_method") === "S256").toBe(true);
          }
        }
      });
      await visit(page);
      await expect.poll(async () => (await status(isolated)).authenticated).toBe(true);
      await expect.poll(async () => page.locator(".nav-logout").count()).toBe(1);
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
        await privateAction(() => page.goto(business + "/login?interactive=true"));
        await expect
          .poll(() => new URL(page.url()).origin === hearth && new URL(page.url()).pathname === "/login")
          .toBe(true);
        await visit(oldTab);
        expect((await status(isolated)).authenticated).toBe(false);
        await login(page, false);
        await expect.poll(async () => (await status(isolated)).authenticated).toBe(true);
        expect((await status(isolated)).suppressed).toBe(false);
        await signedInNavigation(page);
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
    await expect.poll(async () => (await status(parallel)).authenticated).toBe(true);
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
  } finally {
    for (const owned of contexts) await owned.close();
  }
});
