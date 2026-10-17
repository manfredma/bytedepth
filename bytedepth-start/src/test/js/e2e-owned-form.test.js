import { submitOwnedForm, updateOwnedArticle } from "../../../../tests/e2e/helpers/owned-form.js";
import { authenticationCookies, loginAsAdmin } from "../../../../tests/e2e/helpers/admin-auth.js";

describe("owned staging fixture requests", () => {
  let previousFetch;
  beforeEach(() => {
    document.head.innerHTML = "";
    document.body.innerHTML = "";
    previousFetch = globalThis.fetch;
  });
  afterEach(() => {
    globalThis.fetch = previousFetch;
  });
  const page = { evaluate: async (fn, args) => fn(args) };
  test("publishes with the matching server-rendered form token when the list has no CSRF meta", async () => {
    document.body.innerHTML =
      '<form action="/admin/posts/2/publish"><input name="_csrf" value="other"></form><form action="/admin/posts/1/publish"><input name="_csrf" value="publish-token"></form>';
    let body;
    globalThis.fetch = async (path, options) => {
      body = options.body;
      return { ok: true, status: 200, redirected: true, url: "http://localhost/admin/posts" };
    };
    await submitOwnedForm(page, "/admin/posts/1/publish", {});
    expect(body.get("_csrf")).toBe("publish-token");
  });
  test("uses editor meta for create/update and never permits fields to replace its token", async () => {
    document.head.innerHTML = '<meta name="_csrf" content="editor-token">';
    let body;
    globalThis.fetch = async (path, options) => {
      body = options.body;
      return { ok: true, status: 200, redirected: true, url: "http://localhost/admin/posts" };
    };
    await submitOwnedForm(page, "/admin/posts", { title: "fixture", _csrf: "wrong" });
    expect(body.get("_csrf")).toBe("editor-token");
    expect(body.get("title")).toBe("fixture");
  });
  test("missing token fails before any request and cannot borrow another article form token", async () => {
    document.body.innerHTML = '<form action="/admin/posts/2/publish"><input name="_csrf" value="other"></form>';
    let requests = 0;
    globalThis.fetch = async () => {
      requests++;
      return { ok: true };
    };
    await expect(submitOwnedForm(page, "/admin/posts/1/publish", {})).rejects.toThrow(/CSRF/);
    expect(requests).toBe(0);
  });
  test("403 remains a failure with the real HTTP status", async () => {
    document.head.innerHTML = '<meta name="_csrf" content="token">';
    globalThis.fetch = async () => ({ ok: false, status: 403 });
    await expect(submitOwnedForm(page, "/admin/posts", {})).rejects.toThrow(/403/);
  });
  test("shares only the authentication cookie and preserves its transport attributes", () => {
    const session = {
      name: "SESSION",
      value: "opaque",
      domain: "staging.example",
      path: "/",
      httpOnly: true,
      secure: true,
      sameSite: "Lax",
      expires: -1,
    };
    expect(
      authenticationCookies({
        cookies: [{ name: "bd_annotation_visitor", value: "reader" }, session, { name: "XSRF-TOKEN", value: "csrf" }],
      }),
    ).toEqual([session]);
  });
  test("missing authentication state fails instead of silently repeating login attempts", () => {
    expect(() => authenticationCookies({ cookies: [{ name: "bd_annotation_visitor", value: "reader" }] })).toThrow(
      /session/i,
    );
  });
  test("forty admin contexts reuse one run session without posting another login or sharing reader cookies", async () => {
    const old = process.env.E2E_ADMIN_COOKIES;
    const session = { name: "SESSION", value: "opaque", domain: "staging.example", path: "/" };
    process.env.E2E_ADMIN_COOKIES = JSON.stringify([session]);
    const received = [];
    const page = {
      context: () => ({ addCookies: async (cookies) => received.push(cookies) }),
      locator: () => {
        throw new Error("Repeated credential login");
      },
    };
    try {
      for (let i = 0; i < 40; i++) await loginAsAdmin(page);
      expect(received).toEqual(Array.from({ length: 40 }, () => [session]));
    } finally {
      if (old === undefined) delete process.env.E2E_ADMIN_COOKIES;
      else process.env.E2E_ADMIN_COOKIES = old;
    }
  });
  test("a successful HTTP response cannot pretend a rejected or lost content update was persisted", async () => {
    const page = {
      goto: async () => {},
      locator: (selector) => ({
        getAttribute: async () => (selector.includes("option") ? "1" : "/admin/posts/1"),
        textContent: async () => "old content",
      }),
      evaluate: async () => ({ ok: true, status: 200, url: "https://staging.example/admin/posts", redirected: true }),
    };
    await expect(
      updateOwnedArticle(page, { editPath: "/admin/posts/1/edit", title: "owned" }, "new content"),
    ).rejects.toThrow(/persisted content/);
  });

  for (const result of [
    { ok: true, status: 200, redirected: true, url: "http://localhost/login" },
    { ok: true, status: 200, redirected: false, url: "http://localhost/admin/posts/1" },
  ]) {
    test(`does not accept a login/validation page as form success (${result.url})`, async () => {
      document.head.innerHTML = '<meta name="_csrf" content="token">';
      globalThis.fetch = async () => result;
      await expect(submitOwnedForm(page, "/admin/posts/1", {})).rejects.toThrow(/redirect/);
    });
  }

  test("accepts an update only after reading the exact new server-rendered source", async () => {
    const navigations = [];
    const page = {
      goto: async (path, options) => navigations.push([path, options.waitUntil]),
      locator: (selector) => ({
        getAttribute: async () => (selector.includes("option") ? "1" : "/admin/posts/1"),
        textContent: async () => "new content",
      }),
      evaluate: async () => ({ ok: true, status: 200, url: "https://staging.example/admin/posts", redirected: true }),
    };
    await updateOwnedArticle(page, { editPath: "/admin/posts/1/edit", title: "owned" }, "new content");
    expect(navigations).toEqual([
      ["/admin/posts/1/edit", "domcontentloaded"],
      ["/admin/posts/1/edit", "domcontentloaded"],
    ]);
  });
});
