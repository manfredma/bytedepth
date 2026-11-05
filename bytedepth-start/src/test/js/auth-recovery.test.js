import { createAuthRecovery } from "../../main/resources/static/js/auth-recovery.js";

function fixture() {
  document.body.innerHTML = "";
  localStorage.clear();
  const fetch = vi
    .fn()
    .mockResolvedValueOnce({
      ok: true,
      json: async () => ({ authenticated: false, suppressed: false, csrfToken: "test-csrf" }),
    })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: "test-csrf" }) })
    .mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ start: "/oauth2/authorization/hearth?silent=true" }),
    });
  const reload = vi.fn();
  const locks = { request: async (_name, _options, run) => run({}) };
  const browser = {
    document,
    localStorage,
    fetch,
    navigator: { locks },
    location: { origin: "https://staging-bytedepth.bytedepth.cn", reload },
    AbortController,
    setTimeout,
    clearTimeout,
    addEventListener: window.addEventListener.bind(window),
    removeEventListener: window.removeEventListener.bind(window),
  };
  return { browser, fetch, reload, run: createAuthRecovery(browser, false) };
}
async function settle() {
  for (let i = 0; i < 12; i++) await Promise.resolve();
}
function message(frame, outcome, overrides = {}) {
  window.dispatchEvent(
    new MessageEvent("message", {
      origin: "https://staging-bytedepth.bytedepth.cn",
      source: frame.contentWindow,
      data: { type: "bytedepth-auth", outcome },
      ...overrides,
    }),
  );
}

afterEach(() => {
  vi.useRealTimers();
  document.body.innerHTML = "";
});

test("first anonymous public visit starts a hidden authorization without navigating the page", async () => {
  const { run, fetch, reload } = fixture();
  const task = run();
  await settle();
  const frame = document.querySelector("iframe");
  expect(frame).not.toBeNull();
  expect(frame.hidden).toBe(true);
  expect(fetch.mock.calls[2][1].headers["X-CSRF-TOKEN"]).toBe("test-csrf");
  message(frame, "anonymous");
  await task;
  expect(reload).not.toHaveBeenCalled();
  expect(document.querySelector("iframe")).toBeNull();
});

test("successful callback verifies the actual local session before reloading the original page", async () => {
  const { run, fetch, reload } = fixture();
  fetch.mockResolvedValueOnce({ ok: true, json: async () => ({ authenticated: true }) });
  const task = run();
  await settle();
  const frame = document.querySelector("iframe");
  expect(frame).not.toBeNull();
  message(frame, "success");
  await task;
  expect(reload).toHaveBeenCalledOnce();
});

test("failure followed by refresh does not start another authorization during cooldown", async () => {
  const { run, fetch } = fixture();
  const task = run();
  await settle();
  const frame = document.querySelector("iframe");
  expect(frame).not.toBeNull();
  message(frame, "anonymous");
  await task;
  fetch.mockResolvedValueOnce({ ok: true, json: async () => ({ authenticated: false, suppressed: false }) });
  await run();
  expect(fetch).toHaveBeenCalledTimes(4);
  expect(document.querySelector("iframe")).toBeNull();
});

test("timeout removes the hidden frame and leaves the reader in place", async () => {
  vi.useFakeTimers();
  const { run, reload } = fixture();
  const task = run();
  await settle();
  expect(document.querySelector("iframe")).not.toBeNull();
  await vi.advanceTimersByTimeAsync(20_000);
  await task;
  expect(document.querySelector("iframe")).toBeNull();
  expect(reload).not.toHaveBeenCalled();
});

test.each([
  { authenticated: true, suppressed: false },
  { authenticated: false, suppressed: true },
  { authenticated: false, suppressed: false, interactive: true },
])("existing session or explicit logout prevents authorization: %j", async (status) => {
  const { browser, fetch, reload } = fixture();
  fetch.mockReset().mockResolvedValue({ ok: true, json: async () => status });
  await createAuthRecovery(browser, true)();
  expect(fetch).toHaveBeenCalledOnce();
  expect(document.querySelector("iframe")).toBeNull();
  expect(reload).not.toHaveBeenCalled();
});

test("a different tab's completed authentication refreshes a stale anonymous page", async () => {
  const { run, fetch, reload } = fixture();
  fetch.mockReset().mockResolvedValue({ ok: true, json: async () => ({ authenticated: true }) });
  await run();
  expect(reload).toHaveBeenCalledOnce();
});

test("unavailable or occupied Web Locks never starts an uncoordinated request", async () => {
  const { browser, fetch } = fixture();
  browser.navigator.locks = null;
  await createAuthRecovery(browser, false)();
  browser.navigator.locks = { request: async (_name, _options, run) => run(null) };
  await createAuthRecovery(browser, false)();
  expect(fetch).not.toHaveBeenCalled();
});

test("concurrent tabs hold the shared lock until the iframe has completed", async () => {
  const { browser, fetch, run } = fixture();
  let held = false;
  browser.navigator.locks.request = async (_name, _options, action) => {
    if (held) return action(null);
    held = true;
    try {
      return await action({});
    } finally {
      held = false;
    }
  };
  const first = run();
  await settle();
  const frame = document.querySelector("iframe");
  expect(frame).not.toBeNull();
  await createAuthRecovery(browser, false)();
  expect(fetch).toHaveBeenCalledTimes(3);
  message(frame, "anonymous");
  await first;
  expect(held).toBe(false);
});

test("cooldown expires for a later visit so a new Hearth login can be discovered", async () => {
  const { run } = fixture();
  localStorage.setItem("bytedepth.auth.last-attempt", String(Date.now() - 300_001));
  const task = run();
  await settle();
  const frame = document.querySelector("iframe");
  expect(frame).not.toBeNull();
  message(frame, "anonymous");
  await task;
});

test.each([
  { origin: "https://untrusted.example" },
  { source: window },
  { data: null },
  { data: { type: "other", outcome: "success" } },
  { data: { type: "bytedepth-auth", outcome: "unknown" } },
])("forged or malformed completion cannot refresh the reader: %j", async (overrides) => {
  const { run, reload } = fixture();
  const task = run();
  await settle();
  const frame = document.querySelector("iframe");
  expect(frame).not.toBeNull();
  message(frame, "success", overrides);
  await settle();
  expect(document.querySelector("iframe")).toBe(frame);
  expect(reload).not.toHaveBeenCalled();
  message(frame, "anonymous");
  await task;
});

test("a claimed success with no actual local authentication does not refresh", async () => {
  const { run, fetch, reload } = fixture();
  fetch.mockResolvedValueOnce({ ok: true, json: async () => ({ authenticated: false }) });
  const task = run();
  await settle();
  const frame = document.querySelector("iframe");
  expect(frame).not.toBeNull();
  message(frame, "success");
  await task;
  expect(reload).not.toHaveBeenCalled();
});

test.each(["status", "start", "204", "foreign-target", "storage-read", "storage-write", "network"])(
  "failures stop without navigating: %s",
  async (kind) => {
    const { browser, fetch, reload } = fixture();
    if (kind === "status") fetch.mockReset().mockResolvedValue({ ok: false });
    if (kind === "start")
      fetch
        .mockReset()
        .mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: "test" }) })
        .mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: "test" }) })
        .mockResolvedValueOnce({ ok: false });
    if (kind === "204")
      fetch
        .mockReset()
        .mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: "test" }) })
        .mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: "test" }) })
        .mockResolvedValueOnce({ ok: true, status: 204 });
    if (kind === "foreign-target")
      fetch
        .mockReset()
        .mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: "test" }) })
        .mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: "test" }) })
        .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ start: "https://untrusted.example" }) });
    if (kind === "storage-read")
      browser.localStorage = {
        getItem: () => {
          throw new Error("disabled");
        },
      };
    if (kind === "storage-write")
      browser.localStorage = {
        getItem: () => null,
        setItem: () => {
          throw new Error("full");
        },
      };
    if (kind === "network") fetch.mockReset().mockRejectedValue(new Error("network"));
    await createAuthRecovery(browser, false)();
    expect(document.querySelector("iframe")).toBeNull();
    expect(reload).not.toHaveBeenCalled();
  },
);

test("background auth bootstrap rechecks visible and restored pages without periodic polling", async () => {
  const { browser, fetch } = fixture();
  browser.navigator.locks.request = async (_name, _options, action) => action(null);
  browser.addEventListener = vi.fn();
  const listeners = [];
  const add = vi.spyOn(document, "addEventListener").mockImplementation((...args) => listeners.push(args));
  document.body.innerHTML = '<script data-auth-recovery data-authenticated="true"></script>';
  vi.stubGlobal("window", browser);
  vi.resetModules();
  await import("../../main/resources/static/js/auth-recovery.js");
  expect(browser.addEventListener.mock.calls[0][0]).toBe("pageshow");
  await browser.addEventListener.mock.calls[0][1]();
  const visibility = listeners.find(([name]) => name === "visibilitychange")[1];
  const hidden = vi.spyOn(document, "hidden", "get").mockReturnValue(true);
  visibility();
  hidden.mockReturnValue(false);
  visibility();
  await settle();
  expect(fetch).not.toHaveBeenCalled();
  hidden.mockRestore();
  add.mockRestore();
  vi.unstubAllGlobals();
});

test("a late prepare response after timeout cannot leave an orphaned frame", async () => {
  vi.useFakeTimers();
  const { run, fetch, reload } = fixture();
  let deliver;
  fetch
    .mockReset()
    .mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: "test" }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: "test" }) })
    .mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () =>
        new Promise((resolve) => {
          deliver = resolve;
        }),
    });
  const task = run();
  await settle();
  await vi.advanceTimersByTimeAsync(20_000);
  deliver({ start: "/oauth2/authorization/hearth?silent=true" });
  await settle();
  expect(document.querySelector("iframe")).toBeNull();
  await task;
  expect(reload).not.toHaveBeenCalled();
});

test("a waiting tab rechecks the completed authentication and refreshes without a second authorization", async () => {
  const { browser, fetch, reload } = fixture();
  let release;
  const occupied = new Promise((resolve) => {
    release = resolve;
  });
  browser.navigator.locks.request = async (_name, options, run) => {
    if (options.ifAvailable) return run(null);
    await occupied;
    return run({});
  };
  fetch.mockReset().mockResolvedValue({ ok: true, json: async () => ({ authenticated: true }) });
  const waiting = createAuthRecovery(browser, false)();
  await settle();
  expect(fetch).not.toHaveBeenCalled();
  release();
  await waiting;
  expect(reload).toHaveBeenCalledOnce();
  expect(fetch).toHaveBeenCalledOnce();
});
