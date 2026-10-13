import { vi } from "vitest";

const fs = require("fs");
const path = require("path");

const storeSource = fs.readFileSync(
  path.resolve(__dirname, "../../main/resources/static/js/reading-history-store.js"),
  "utf-8",
);

describe("reading history event upload", () => {
  beforeEach(() => {
    localStorage.clear();
    document.head.innerHTML = '<meta name="_csrf" content="session-token">';
    eval(storeSource);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete window.ReadingHistoryStore;
  });

  test("a logged-in reader's open event reaches the server instead of leaving history empty", async () => {
    const serverEvents = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url, request) => {
        const accepted = request.headers["X-CSRF-TOKEN"] === "session-token";
        if (accepted) {
          serverEvents.push(JSON.parse(request.body));
        }
        return { ok: accepted, status: accepted ? 202 : 403 };
      }),
    );
    const store = window.ReadingHistoryStore.create({
      authenticated: true,
      eventsUrl: "/posts/java/reading-events",
      postSlug: "java",
      sessionId: "reading-session",
    });

    store.recordOpen();
    await vi.waitFor(() => expect(serverEvents).toHaveLength(1), { timeout: 100 });

    expect(serverEvents[0]).toMatchObject({ postSlug: "java", type: "READ_OPEN" });
    expect(JSON.parse(localStorage.getItem("bytedepth.reading-events.v1"))).toEqual([]);
  });

  test("an anonymous reader keeps history locally without uploading", () => {
    vi.stubGlobal("fetch", vi.fn());
    const store = window.ReadingHistoryStore.create({
      authenticated: false,
      eventsUrl: "/posts/java/reading-events",
      postSlug: "java",
      sessionId: "reading-session",
    });

    store.recordOpen();

    expect(store.getSummary().readCount).toBe(1);
    expect(fetch).not.toHaveBeenCalled();
  });

  test.each([
    ["recordHeartbeat", "READ_HEARTBEAT"],
    ["recordComplete", "READ_COMPLETE"],
    ["recordClose", "READ_CLOSE"],
  ])("%s includes the CSRF header, including keepalive uploads", async (method, type) => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: true, status: 202 })),
    );
    const store = window.ReadingHistoryStore.create({
      authenticated: true,
      eventsUrl: "/posts/java/reading-events",
      postSlug: "java",
      sessionId: "reading-session",
    });

    store[method](15, 80);
    await vi.waitFor(() => expect(JSON.parse(localStorage.getItem("bytedepth.reading-events.v1"))).toEqual([]));

    expect(fetch).toHaveBeenCalledWith(
      "/posts/java/reading-events",
      expect.objectContaining({
        method: "POST",
        credentials: "same-origin",
        keepalive: true,
        headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": "session-token" },
        body: expect.any(String),
      }),
    );
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toMatchObject({ type, activeSecondsDelta: 15 });
  });

  test("a rejected event stays queued and retries with the current page token", async () => {
    document.head.innerHTML = "";
    const uploadedEvents = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url, request) => {
        const accepted = request.headers["X-CSRF-TOKEN"] === "new-session-token";
        if (accepted) {
          uploadedEvents.push(JSON.parse(request.body));
        }
        return { ok: accepted, status: accepted ? 202 : 403 };
      }),
    );
    const store = window.ReadingHistoryStore.create({
      authenticated: true,
      eventsUrl: "/posts/java/reading-events",
      postSlug: "java",
      sessionId: "reading-session",
    });
    store.recordOpen();
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    const pending = JSON.parse(localStorage.getItem("bytedepth.reading-events.v1"));
    expect(pending).toHaveLength(1);

    document.head.innerHTML = '<meta name="_csrf" content="new-session-token">';
    await store.flushAuthenticatedOutbox();

    expect(uploadedEvents).toHaveLength(1);
    expect(uploadedEvents[0].eventId).toBe(pending[0].eventId);
    expect(JSON.parse(localStorage.getItem("bytedepth.reading-events.v1"))).toEqual([]);
  });

  test("logging in does not upload previously anonymous events", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: true, status: 202 })),
    );
    const options = { eventsUrl: "/posts/java/reading-events", postSlug: "java", sessionId: "reading-session" };
    window.ReadingHistoryStore.create({ ...options, authenticated: false }).recordOpen();
    const store = window.ReadingHistoryStore.create({ ...options, authenticated: true });

    store.recordOpen();
    await vi.waitFor(() => expect(JSON.parse(localStorage.getItem("bytedepth.reading-events.v1"))).toHaveLength(1));

    expect(fetch).toHaveBeenCalledTimes(1);
    expect(JSON.parse(localStorage.getItem("bytedepth.reading-events.v1"))[0].serverOwned).toBe(false);
  });
});
