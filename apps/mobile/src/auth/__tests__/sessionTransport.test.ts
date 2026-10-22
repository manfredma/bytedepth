import { MobileApi, type TransportOptions, type TransportResponse } from "../../api/MobileApi";
import { SessionStore, type SecureValues } from "../SessionStore";
import { InstallIdentity } from "../InstallIdentity";

class Values implements SecureValues {
  readonly entries = new Map<string, string>();
  async get(key: string) {
    return this.entries.get(key) ?? null;
  }
  async set(key: string, value: string) {
    this.entries.set(key, value);
  }
  async remove(key: string) {
    this.entries.delete(key);
  }
}
function response(status: number, body: unknown = undefined, extra: Record<string, string> = {}): TransportResponse {
  const headers: Record<string, string> = { "content-type": "application/json", ...extra };
  return {
    status,
    headers: { get: (name) => headers[name.toLowerCase()] ?? null },
    text: async () => (body === undefined ? "" : JSON.stringify(body)),
  };
}
function fixture(responses: TransportResponse[]) {
  const requests: { url: string; options: TransportOptions }[] = [];
  const secure = new Values();
  const sessions = new SessionStore(secure);
  const api = new MobileApi(
    async (url, options) => {
      requests.push({ url, options });
      const next = responses.shift();
      if (!next) throw new Error("Unexpected request");
      return next;
    },
    sessions,
    async () => "app:12345678-1234-4123-8123-123456789abc",
  );
  return { requests, api, secure, sessions };
}
const token = (value = "csrf1") =>
  response(200, { headerName: "X-CSRF-TOKEN", token: value }, { "x-app-session": "anon-session" });

it("establishes anonymous session and CSRF before rating without login or Cookie", async () => {
  const f = fixture([token(), response(204)]);
  await f.api.request("/posts/9007199254740993/rating", { method: "PUT", body: { score: 4 } });
  expect(f.requests.map((r) => [r.url, r.options.method])).toEqual([
    ["https://staging-bytedepth.bytedepth.cn/api/mobile/v1/csrf", "GET"],
    ["https://staging-bytedepth.bytedepth.cn/api/mobile/v1/posts/9007199254740993/rating", "PUT"],
  ]);
  expect(f.requests[1].options.headers).toEqual({
    Accept: "application/json",
    "Content-Type": "application/json",
    "X-App-Session": "anon-session",
    "X-CSRF-TOKEN": "csrf1",
    "X-App-Visitor": "app:12345678-1234-4123-8123-123456789abc",
  });
  expect(f.requests.every((r) => r.options.credentials === "omit" && r.options.redirect === "error")).toBe(true);
});
it("uses standard form login and rotated session then fetches fresh CSRF", async () => {
  const f = fixture([
    token(),
    response(204, undefined, { "x-app-session": "authenticated" }),
    response(200, { headerName: "X-CSRF-TOKEN", token: "csrf2" }),
  ]);
  await f.api.request("/login", { method: "POST", body: { username: "a+b", password: "secret&123" } });
  await f.api.csrf();
  expect(f.requests[1].options.body).toBe("username=a%2Bb&password=secret%26123");
  expect(f.requests[1].options.headers["Content-Type"]).toBe("application/x-www-form-urlencoded");
  expect(f.requests[2].options.headers["X-App-Session"]).toBe("authenticated");
  expect((await f.sessions.snapshot()).id).toBe("authenticated");
  expect([...f.secure.entries.values()]).toEqual(["authenticated"]);
});
it("does not attach account identity to public reads or expose implicit Cookie auth", async () => {
  const f = fixture([token(), response(200, { items: [] })]);
  await f.api.csrf();
  await f.api.request("/posts", { method: "GET" });
  expect(f.requests[1].options.headers).toEqual({ Accept: "application/json" });
});
it("ignores session headers on an unrelated public article response", async () => {
  const f = fixture([response(200, { items: [] }, { "x-app-session": "new-anonymous" })]);
  await f.sessions.accept(await f.sessions.snapshot(), "logged-in");
  await f.api.request("/posts", { method: "GET" });
  expect((await f.sessions.snapshot()).id).toBe("logged-in");
});
it.each(["https://evil.invalid/x", "//evil.invalid/x", "/../admin", "/posts#secret"])(
  "rejects API escape %s before fetching",
  async (path) => {
    const f = fixture([]);
    await expect(f.api.request(path, { method: "GET" })).rejects.toThrow();
    expect(f.requests).toEqual([]);
  },
);
it("returns typed errors and respects Retry-After without replaying a write", async () => {
  const f = fixture([token(), response(429, { code: "RATE_LIMITED", message: "稍后重试" }, { "retry-after": "12" })]);
  await expect(f.api.request("/posts/1/rating", { method: "PUT", body: { score: 4 } })).rejects.toMatchObject({
    status: 429,
    code: "RATE_LIMITED",
    retryAfterMs: 12000,
  });
  expect(f.requests).toHaveLength(2);
});
it("does not interpret HTML gateway failure as unavailable public content", async () => {
  const f = fixture([{ status: 404, headers: { get: () => "text/html" }, text: async () => "<html>gateway</html>" }]);
  await expect(f.api.request("/posts/1/state", { method: "GET" })).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});
it("invalidates private responses when app backgrounds without destroying session", async () => {
  const secure = new Values();
  const sessions = new SessionStore(secure);
  let finish!: (r: TransportResponse) => void;
  let started!: () => void;
  const ready = new Promise<void>((resolve) => {
    started = resolve;
  });
  const api = new MobileApi(
    async () =>
      new Promise((resolve) => {
        finish = resolve;
        started();
      }),
    sessions,
    async () => "app:visitor",
  );
  const pending = api.request("/me", { method: "GET", authenticated: true });
  await ready;
  sessions.invalidatePrivate();
  finish(response(200, { id: "1", username: "old", permissions: [] }));
  await expect(pending).rejects.toMatchObject({ code: "STALE_RESPONSE" });
});
it("does not let delayed old-account 401 body clear a new session", async () => {
  const secure = new Values();
  const sessions = new SessionStore(secure);
  await sessions.accept(await sessions.snapshot(), "old-session");
  let finish!: (text: string) => void;
  let started!: () => void;
  const ready = new Promise<void>((resolve) => {
    started = resolve;
  });
  const api = new MobileApi(
    async () => ({
      status: 401,
      headers: { get: (name) => (name.toLowerCase() === "content-type" ? "application/json" : null) },
      text: () =>
        new Promise((resolve) => {
          finish = resolve;
          started();
        }),
    }),
    sessions,
    async () => "app:visitor",
  );
  const pending = api.request("/me", { method: "GET", authenticated: true });
  const rejected = expect(pending).rejects.toMatchObject({ code: "STALE_RESPONSE" });
  await ready;
  await sessions.accept(await sessions.snapshot(), "new-session");
  finish('{"code":"AUTHENTICATION_FAILED","message":"expired"}');
  await rejected;
  expect((await sessions.snapshot()).id).toBe("new-session");
});
it("prevents a late session-creating response from resurrecting logout credentials", async () => {
  const secure = new Values();
  const sessions = new SessionStore(secure);
  const old = await sessions.snapshot();
  await sessions.clear();
  await expect(sessions.accept(old, "late-session")).rejects.toMatchObject({ code: "STALE_RESPONSE" });
  expect((await sessions.snapshot()).id).toBeNull();
  expect(secure.entries.size).toBe(0);
});
it("discards CSRF after CSRF_INVALID without automatically resubmitting writes", async () => {
  const f = fixture([token(), response(403, { code: "CSRF_INVALID", message: "请重试" }), token("new")]);
  await expect(f.api.request("/posts/1/rating", { method: "PUT", body: { score: 2 } })).rejects.toMatchObject({
    code: "CSRF_INVALID",
  });
  await f.api.csrf();
  expect(f.requests.map((r) => r.options.method)).toEqual(["GET", "PUT", "GET"]);
});
it("creates install identity once and resets secure residue when install marker disappears", async () => {
  const secure = new Values();
  const marker = new Values();
  const ids = ["11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"];
  const first = new InstallIdentity(secure, marker, () => ids.shift()!);
  expect(await first.visitorId()).toBe("app:11111111-1111-4111-8111-111111111111");
  expect(
    await new InstallIdentity(secure, marker, () => {
      throw new Error("must reuse");
    }).visitorId(),
  ).toBe("app:11111111-1111-4111-8111-111111111111");
  marker.entries.clear();
  expect(await new InstallIdentity(secure, marker, () => ids.shift()!).visitorId()).toBe(
    "app:22222222-2222-4222-8222-222222222222",
  );
});
it("blocks a write if logout invalidates CSRF between preparation and request", async () => {
  const f = fixture([token()]);
  await f.api.csrf();
  const pending = f.api.request("/posts/1/rating", { method: "PUT", body: { score: 2 } });
  const rejected = expect(pending).rejects.toMatchObject({ code: "STALE_RESPONSE" });
  await f.sessions.clear();
  await rejected;
  expect(f.requests).toHaveLength(1);
});
it("rejects malformed login input before sending credentials", async () => {
  const f = fixture([token()]);
  await expect(
    f.api.request("/login", { method: "POST", body: { username: 2, password: "secret123" } }),
  ).rejects.toMatchObject({ code: "INVALID_REQUEST" });
  expect(f.requests).toHaveLength(1);
});
it.each([null, [], { items: [] }])("rejects malformed authentication response %j", async (value) => {
  const f = fixture([response(200, value, { "x-app-session": "anonymous" })]);
  await expect(f.api.csrf()).rejects.toThrow();
});
it.each([
  { headerName: "Authorization", token: "x" },
  { headerName: "X-CSRF-TOKEN", token: "" },
])("rejects untrusted CSRF metadata %j", async (value) => {
  const f = fixture([response(200, value, { "x-app-session": "anonymous" })]);
  await expect(f.api.csrf()).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});
it("requires a real session when establishing anonymous CSRF", async () => {
  const f = fixture([response(200, { headerName: "X-CSRF-TOKEN", token: "token" })]);
  await expect(f.api.csrf()).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});
it("clears credentials after a current authenticated 401 without retry", async () => {
  const f = fixture([response(401, { code: "UNAUTHENTICATED", message: "登录已过期" })]);
  await f.sessions.accept(await f.sessions.snapshot(), "expired-session");
  await expect(f.api.request("/me", { method: "GET", authenticated: true })).rejects.toMatchObject({
    status: 401,
    code: "UNAUTHENTICATED",
  });
  expect((await f.sessions.snapshot()).id).toBeNull();
  expect(f.requests).toHaveLength(1);
});
it.each([null, [], { message: "untyped" }])("rejects malformed error envelope %j", async (value) => {
  const f = fixture([response(500, value)]);
  await expect(f.api.request("/posts", { method: "GET" })).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
});
it("rejects broken JSON and missing content type", async () => {
  for (const value of [
    { status: 500, headers: { get: () => null }, text: async () => "" },
    { status: 200, headers: { get: () => "application/json" }, text: async () => "{" },
  ]) {
    const f = fixture([value]);
    await expect(f.api.request("/posts", { method: "GET" })).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  }
});
it("shares one in-flight CSRF request across concurrent writes", async () => {
  const sessions = new SessionStore(new Values());
  let requests = 0;
  let finish!: (value: TransportResponse) => void;
  const api = new MobileApi(
    async () => {
      requests++;
      return new Promise((resolve) => {
        finish = resolve;
      });
    },
    sessions,
    async () => "app:visitor",
  );
  const first = api.csrf();
  const second = api.csrf();
  while (!finish) await Promise.resolve();
  finish(token());
  await Promise.all([first, second]);
  expect(requests).toBe(1);
});
it("allows retrying identity creation after secure storage failure", async () => {
  const secure = new Values();
  let failed = false;
  const marker: SecureValues = {
    get: async () => {
      if (!failed) {
        failed = true;
        throw new Error("unavailable");
      }
      return null;
    },
    set: async () => undefined,
    remove: async () => undefined,
  };
  const identity = new InstallIdentity(secure, marker, () => "11111111-1111-4111-8111-111111111111");
  await expect(identity.visitorId()).rejects.toThrow("unavailable");
  expect(await identity.visitorId()).toBe("app:11111111-1111-4111-8111-111111111111");
});
it("refuses invalid UUID sources instead of emitting unstable visitor identifiers", async () => {
  await expect(new InstallIdentity(new Values(), new Values(), () => "not-a-uuid").visitorId()).rejects.toThrow(
    "Invalid installation identity",
  );
});
it("expires explicit empty session headers and removes disconnected observers", async () => {
  const sessions = new SessionStore(new Values());
  let notifications = 0;
  const stop = sessions.onClear(() => notifications++);
  stop();
  await sessions.accept(await sessions.snapshot(), "session");
  await sessions.accept(await sessions.snapshot(), "");
  expect((await sessions.snapshot()).id).toBeNull();
  await sessions.clear();
  expect(notifications).toBe(0);
});
it("completes logout deletion after an earlier secure store write failed", async () => {
  const values = new Values();
  values.set = async () => {
    throw new Error("write failed");
  };
  const sessions = new SessionStore(values);
  await expect(sessions.accept(await sessions.snapshot(), "session")).rejects.toThrow("write failed");
  await sessions.clear();
  expect((await sessions.snapshot()).id).toBeNull();
  expect(values.entries.size).toBe(0);
});
it("does not send a rating with credentials invalidated while loading installation identity", async () => {
  const sessions = new SessionStore(new Values());
  let visit!: () => void;
  let started!: () => void;
  const ready = new Promise<void>((r) => {
    started = r;
  });
  const requests: string[] = [];
  const api = new MobileApi(
    async (url) => {
      requests.push(url);
      return url.endsWith("/csrf") ? token() : response(204);
    },
    sessions,
    async () => {
      started();
      await new Promise<void>((r) => {
        visit = r;
      });
      return "app:visitor";
    },
  );
  const pending = api.request("/posts/1/rating", { method: "PUT", body: { score: 3 } });
  const rejected = expect(pending).rejects.toMatchObject({ code: "STALE_RESPONSE" });
  await ready;
  await sessions.clear();
  visit();
  await rejected;
  expect(requests).toHaveLength(1);
});
it("rejects a successful old private response whose JSON body arrives after account change", async () => {
  const sessions = new SessionStore(new Values());
  let finish!: (text: string) => void;
  let started!: () => void;
  const ready = new Promise<void>((r) => {
    started = r;
  });
  const api = new MobileApi(
    async () => ({
      status: 200,
      headers: { get: () => "application/json" },
      text: () =>
        new Promise<string>((r) => {
          finish = r;
          started();
        }),
    }),
    sessions,
    async () => "app:visitor",
  );
  const pending = api.request("/me", { method: "GET", authenticated: true });
  const rejected = expect(pending).rejects.toMatchObject({ code: "STALE_RESPONSE" });
  await ready;
  sessions.invalidatePrivate();
  finish('{"id":"1"}');
  await rejected;
});
it("keeps a newer CSRF negotiation pending when an older generation finishes", async () => {
  const sessions = new SessionStore(new Values());
  const finishes: ((value: TransportResponse) => void)[] = [];
  const api = new MobileApi(
    async () => new Promise<TransportResponse>((r) => finishes.push(r)),
    sessions,
    async () => "app:visitor",
  );
  const old = api.csrf();
  const rejected = expect(old).rejects.toMatchObject({ code: "STALE_RESPONSE" });
  while (finishes.length < 1) await Promise.resolve();
  await sessions.clear();
  const next = api.csrf();
  while (finishes.length < 2) await Promise.resolve();
  finishes[0](token());
  await rejected;
  const shared = api.csrf();
  await Promise.resolve();
  expect(finishes).toHaveLength(2);
  finishes[1](token("new"));
  await Promise.all([next, shared]);
});
it("rejects null login credentials as a typed client input error", async () => {
  const f = fixture([token()]);
  await expect(f.api.request("/login", { method: "POST", body: null })).rejects.toMatchObject({
    code: "INVALID_REQUEST",
  });
  expect(f.requests).toHaveLength(1);
});
