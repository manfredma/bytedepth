(function (global) {
  "use strict";
  const EVENTS_KEY = "bytedepth.reading-events.v1";
  const HISTORY_KEY = "bytedepth.reading-history.v1";
  const MAX_HISTORY = 1000;
  const EVENT_RETENTION_MS = 7 * 24 * 60 * 60 * 1000;
  function storage() {
    try {
      const value = global.localStorage;
      const probe = "__bytedepth_reading_probe__";
      value.setItem(probe, "1");
      value.removeItem(probe);
      return value;
    } catch {
      return null;
    }
  }
  function read(store, key, fallback) {
    if (!store) {
      return fallback;
    }
    try {
      const value = JSON.parse(store.getItem(key) || "null");
      return value && typeof value === "object" ? value : fallback;
    } catch {
      return fallback;
    }
  }
  function write(store, key, value) {
    if (!store) {
      return false;
    }
    try {
      store.setItem(key, JSON.stringify(value));
      return true;
    } catch {
      return false;
    }
  }
  function create(options) {
    const store = storage();
    const authenticated = options.authenticated === true;
    const eventsUrl = options.eventsUrl;
    const postSlug = options.postSlug;
    const sessionId = options.sessionId || crypto.randomUUID();
    let history = read(store, HISTORY_KEY, {});
    let outbox = read(store, EVENTS_KEY, []);
    if (!Array.isArray(outbox)) {
      outbox = [];
    }
    function prune() {
      const cutoff = Date.now() - EVENT_RETENTION_MS;
      outbox = outbox.filter((item) => item.createdAt >= cutoff);
      history = Object.fromEntries(
        Object.entries(history)
          .sort((a, b) => String(b[1].lastReadAt).localeCompare(String(a[1].lastReadAt)))
          .slice(0, MAX_HISTORY),
      );
      write(store, EVENTS_KEY, outbox);
      write(store, HISTORY_KEY, history);
    }
    function enqueue(type, activeSecondsDelta, maxScrollDepth) {
      const now = new Date().toISOString();
      const event = {
        eventId: crypto.randomUUID(),
        sessionId,
        postSlug,
        type,
        activeSecondsDelta,
        maxScrollDepth,
        occurredAt: now,
        createdAt: Date.now(),
        serverOwned: authenticated,
      };
      const current = history[postSlug] || {
        postSlug,
        titleSnapshot: options.title || "",
        readCount: 0,
        totalActiveSeconds: 0,
        firstReadAt: now,
        lastReadAt: now,
      };
      if (type === "READ_OPEN") {
        current.readCount += 1;
      }
      current.totalActiveSeconds += activeSecondsDelta;
      current.lastReadAt = now;
      history[postSlug] = current;
      outbox.push(event);
      prune();
      void flush();
      return current;
    }
    async function flush() {
      if (!authenticated || !eventsUrl || typeof fetch !== "function") {
        return;
      }
      for (const event of outbox.filter((item) => item.serverOwned)) {
        try {
          const response = await fetch(eventsUrl, {
            method: "POST",
            credentials: "same-origin",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(event),
            keepalive: true,
          });
          if (!response.ok && response.status !== 202) {
            break;
          }
          outbox = outbox.filter((item) => item.eventId !== event.eventId);
          write(store, EVENTS_KEY, outbox);
        } catch {
          break;
        }
      }
    }
    prune();
    return {
      recordOpen: () => enqueue("READ_OPEN", 0, 0),
      recordHeartbeat: (seconds, depth) => enqueue("READ_HEARTBEAT", seconds, depth),
      recordComplete: (seconds, depth) => enqueue("READ_COMPLETE", seconds, depth),
      recordClose: (seconds, depth) => enqueue("READ_CLOSE", seconds, depth),
      getSummary: () => history[postSlug] || null,
      listLocalHistory: () => Object.values(history),
      flushAuthenticatedOutbox: flush,
    };
  }
  global.ReadingHistoryStore = { create };
})(window);
