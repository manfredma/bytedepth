import { readFileSync } from "node:fs";
import { createContext, Script } from "node:vm";

// Execute the real worker, with only browser transport/storage replaced. Cache API
// deliberately ignores HTTP no-store, just like the browser: the worker must decide.
const source = readFileSync("bytedepth-start/src/main/resources/static/sw.js", "utf8");
function worker(responseOptions = {}) {
  const listeners = {},
    stored = new Map(),
    calls = [];
  let online = true,
    version = 1,
    identity = "A";
  const response = (text, options = {}) => ({
    ok: true,
    type: "basic",
    text,
    headers: new Headers({ "Cache-Control": "no-store" }),
    clone() {
      return { ...this };
    },
    ...options,
  });
  const cache = {
    match: async (request) => stored.get(request.url || request),
    put: async (request, value) => {
      stored.set(request.url || request, value);
    },
    addAll: async (paths) => {
      for (const path of paths) stored.set(path, response(path));
    },
  };
  const names = new Set(["bytedepth-v8", "bytedepth-v9", "another-app"]);
  const caches = {
    open: async (name) => {
      names.add(name);
      return cache;
    },
    keys: async () => [...names],
    delete: async (name) => names.delete(name),
    match: cache.match,
  };
  const context = createContext({
    URL,
    caches,
    location: { origin: "https://staging.example" },
    self: {
      addEventListener: (name, handler) => {
        listeners[name] = handler;
      },
      skipWaiting: async () => {},
      clients: { claim: async () => {} },
    },
    fetch: async (request) => {
      calls.push(request.url);
      if (!online) throw new Error("offline");
      return response(`${identity}:v${version}`, responseOptions);
    },
  });
  new Script(source, { filename: process.cwd() + "/bytedepth-start/src/main/resources/static/sw.js" }).runInContext(
    context,
  );
  return {
    stored,
    names,
    calls,
    response,
    setVersion: (value) => {
      version = value;
    },
    setIdentity: (value) => {
      identity = value;
    },
    offline: () => {
      online = false;
    },
    lifecycle: async (name) => {
      const pending = [];
      listeners[name]({ waitUntil: (p) => pending.push(p) });
      await Promise.all(pending);
    },
    request: async (path, options = {}) => {
      const request = {
        url: new URL(path, "https://staging.example").href,
        mode: "cors",
        method: "GET",
        headers: new Headers(),
        ...options,
      };
      let result;
      const pending = [];
      listeners.fetch({
        request,
        respondWith: (p) => {
          result = p;
        },
        waitUntil: (p) => pending.push(p),
      });
      // No interception means the browser performs the original network request.
      const value = await (result || context.fetch(request));
      await Promise.all(pending);
      return value;
    },
  };
}

describe("real Service Worker cache boundary", () => {
  test("U11_U38_annotationResponsesAlwaysReachCurrentRevisionAndIdentity", async () => {
    const w = worker();
    const path = "/posts/fixture/annotations";
    expect((await w.request(path)).text).toBe("A:v1");
    w.setVersion(2);
    expect((await w.request(path)).text).toBe("A:v2");
    w.setIdentity("B");
    expect((await w.request(path)).text).toBe("B:v2");
    expect(w.stored.size).toBe(0);
    expect(w.calls).toHaveLength(3);
  });
  test("U38_switchingIdentityCannotReplayAnotherVisitorsPrivateResponse", async () => {
    const w = worker();
    const path = "/posts/fixture/annotations";
    expect((await w.request(path)).text).toBe("A:v1");
    w.setIdentity("B");
    expect((await w.request(path)).text).toBe("B:v1");
    w.offline();
    await expect(w.request(path)).rejects.toThrow("offline");
    expect(w.stored.size).toBe(0);
  });
  test.each([
    "/posts/fixture/annotations",
    "/api/reading-history",
    "/search?q=secret",
    "/posts/fixture",
    "/unknown.json",
    "/js/live.json",
    "/js/unversioned.js",
  ])("dynamic GET %s never reads or writes Cache API", async (path) => {
    const w = worker();
    w.stored.set(`https://staging.example${path}`, w.response("old-private-data"));
    expect((await w.request(path)).text).toBe("A:v1");
    expect(w.stored.get(`https://staging.example${path}`).text).toBe("old-private-data");
    expect(w.calls).toHaveLength(1);
  });
  test.each([
    "/js/annotation-0123456789abcdef0123456789abcdef.js",
    "/css/theme-0123456789abcdef0123456789abcdef.css",
    "/vendor/mermaid/mermaid.min-0123456789abcdef0123456789abcdef.js",
  ])("public static asset %s retains cache-first behavior", async (path) => {
    const w = worker();
    expect((await w.request(path)).text).toBe("A:v1");
    w.setVersion(2);
    expect((await w.request(path)).text).toBe("A:v1");
    expect(w.calls).toHaveLength(1);
  });
  test("new fingerprints fetch new content while the previous URL retains its own bytes", async () => {
    const w = worker();
    const first = "/vendor/mermaid/mermaid.min-0123456789abcdef0123456789abcdef.js";
    const next = "/vendor/mermaid/mermaid.min-abcdef0123456789abcdef0123456789.js";
    expect((await w.request(first)).text).toBe("A:v1");
    w.setVersion(2);
    expect((await w.request(next)).text).toBe("A:v2");
    expect((await w.request(first)).text).toBe("A:v1");
    expect(w.calls).toHaveLength(2);
  });
  test("fixed public URLs update online and only fall back to their cache when offline", async () => {
    const w = worker();
    expect((await w.request("/icons/favicon.svg")).text).toBe("A:v1");
    w.setVersion(2);
    expect((await w.request("/icons/favicon.svg")).text).toBe("A:v2");
    w.offline();
    expect((await w.request("/icons/favicon.svg")).text).toBe("A:v2");
    await expect(w.request("/icons/logo.svg")).rejects.toThrow("offline");
  });
  test.each(["/posts/private", "/posts", "/?token=secret"])(
    "navigation %s never stores personalized HTML and offline uses only neutral fallback",
    async (path) => {
      const w = worker();
      await w.lifecycle("install");
      expect(w.stored.has("/")).toBe(false);
      expect(w.stored.has("/posts")).toBe(false);
      expect(w.stored.has("/columns")).toBe(false);
      expect(w.stored.has("/projects")).toBe(false);
      expect((await w.request(path, { mode: "navigate" })).text).toBe("A:v1");
      expect(w.stored.has(`https://staging.example${path}`)).toBe(false);
      // A historical worker may already have persisted another identity's HTML.
      w.stored.set(`https://staging.example${path}`, w.response("old-private-html"));
      w.offline();
      expect((await w.request(path, { mode: "navigate" })).text).toBe("/offline.html");
    },
  );
  test("activation evicts old bytedepth private caches without touching another application's cache", async () => {
    const w = worker();
    await w.lifecycle("install");
    await w.lifecycle("activate");
    expect(w.names.has("bytedepth-v8")).toBe(false);
    expect(w.names.has("bytedepth-v9")).toBe(false);
    expect(w.names.has("another-app")).toBe(true);
  });
  test.each([{ ok: false }, { type: "opaque" }])(
    "failed or opaque static response is not cached: %j",
    async (options) => {
      const w = worker(options);
      await w.request("/icons/favicon.svg");
      expect(w.stored.size).toBe(0);
    },
  );
  test.each([
    ["/admin/posts", {}],
    ["https://other.example/icons/favicon.svg", {}],
    ["/posts/fixture/annotations", { method: "POST" }],
    ["/posts/fixture/annotations/1", { method: "PATCH" }],
    ["/posts/fixture/annotations/1", { method: "DELETE" }],
    ["/icons/favicon.svg", { headers: new Headers({ "X-CSRF-Refresh": "1" }) }],
  ])("bypass request %s goes directly to network", async (path, options) => {
    const w = worker();
    await w.request(path, options);
    expect(w.calls).toHaveLength(1);
    expect(w.stored.size).toBe(0);
  });
});
