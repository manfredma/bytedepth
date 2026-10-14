const loadPage = async () => {
  vi.resetModules();
  await import("../../main/resources/static/js/reading-history-page.js");
};

const mountPage = (withEmpty = true) => {
  document.body.innerHTML = `<main id="reading-history-page" data-authenticated="false">
    <div class="reading-history-list"><a>stale</a></div>
    ${withEmpty ? '<p class="reading-history-empty" hidden>还没有阅读记录。</p>' : ""}
  </main>`;
};

beforeEach(() => {
  localStorage.clear();
  mountPage();
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({ ok: true, json: async () => [] })),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  document.body.innerHTML = "";
});

test.each([null, "{}", '{"old":{"readCount":1}}', "invalid json"])(
  "anonymous history without valid slugs shows an empty state without requesting metadata: %s",
  async (stored) => {
    if (stored !== null) localStorage.setItem("bytedepth.reading-history.v1", stored);
    await loadPage();
    expect(fetch).not.toHaveBeenCalled();
    expect(document.querySelector(".reading-history-list").children).toHaveLength(0);
    expect(document.querySelector(".reading-history-empty").hidden).toBe(false);
  },
);

test("empty history remains usable without an empty-state element", async () => {
  mountPage(false);
  await loadPage();
  expect(fetch).not.toHaveBeenCalled();
  expect(document.querySelector(".reading-history-list").children).toHaveLength(0);
});

test("unavailable local storage degrades to an empty state without network traffic", async () => {
  vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
    throw new Error("storage blocked");
  });
  await loadPage();
  expect(fetch).not.toHaveBeenCalled();
  expect(document.querySelector(".reading-history-empty").hidden).toBe(false);
});

test.each(["absent", "authenticated", "no list"])("does not load anonymous history for %s pages", async (variant) => {
  if (variant === "absent") document.body.innerHTML = "";
  if (variant === "authenticated") document.querySelector("main").dataset.authenticated = "true";
  if (variant === "no list") document.querySelector(".reading-history-list").remove();
  await loadPage();
  expect(fetch).not.toHaveBeenCalled();
});

test("nonempty anonymous history requests at most twenty newest slugs and renders only available articles", async () => {
  const entries = Object.fromEntries(
    Array.from({ length: 22 }, (_, index) => [
      String(index),
      {
        postSlug: `article-${index}`,
        lastReadAt: String(index).padStart(2, "0"),
        readCount: 2,
        totalActiveSeconds: 30,
      },
    ]),
  );
  localStorage.setItem("bytedepth.reading-history.v1", JSON.stringify(entries));
  fetch.mockResolvedValue({
    ok: true,
    json: async () => [{ slug: "article-21", title: "<safe title>", path: "/posts/article-21" }],
  });
  await loadPage();
  await vi.waitFor(() => expect(document.querySelector(".reading-history-title")?.textContent).toBe("<safe title>"));
  const [url, options] = fetch.mock.calls[0];
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(new URL(url, "https://example.test").searchParams.getAll("slugs")).toEqual(
    Array.from({ length: 20 }, (_, index) => `article-${21 - index}`),
  );
  expect(options.credentials).toBe("same-origin");
  expect(document.querySelectorAll(".reading-history-item")).toHaveLength(1);
  expect(document.querySelector(".reading-history-item").getAttribute("href")).toBe("/posts/article-21");
  expect(document.querySelector(".reading-history-meta").textContent).toBe("2 次 · 30 秒");
  expect(document.querySelector(".reading-history-empty").hidden).toBe(true);
  expect(JSON.parse(localStorage.getItem("bytedepth.reading-history.v1"))).toEqual(entries);
});

test.each(["unpublished", "http failure", "network failure"])(
  "shows an empty state for %s without deleting local history",
  async (variant) => {
    const stored = '{"article":{"postSlug":"article","readCount":1,"totalActiveSeconds":2}}';
    localStorage.setItem("bytedepth.reading-history.v1", stored);
    if (variant === "http failure") fetch.mockResolvedValue({ ok: false });
    if (variant === "network failure") fetch.mockRejectedValue(new Error("offline"));
    await loadPage();
    await vi.waitFor(() => expect(document.querySelector(".reading-history-list").children).toHaveLength(0));
    expect(document.querySelector(".reading-history-empty").hidden).toBe(false);
    expect(localStorage.getItem("bytedepth.reading-history.v1")).toBe(stored);
  },
);

test.each([false, true])(
  "missing empty-state element does not break a metadata request (rejected=%s)",
  async (rejected) => {
    mountPage(false);
    localStorage.setItem("bytedepth.reading-history.v1", '{"article":{"postSlug":"article"}}');
    if (rejected) fetch.mockRejectedValue(new Error("offline"));
    await loadPage();
    await vi.waitFor(() => expect(document.querySelector(".reading-history-list").children).toHaveLength(0));
  },
);
