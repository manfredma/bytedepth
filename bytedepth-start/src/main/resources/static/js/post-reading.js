(function () {
  const tracker = document.getElementById("post-reading-tracker");
  const article = document.querySelector(".content");
  if (!tracker || !article || !window.ReadingHistoryStore) {
    return;
  }
  const activityWindowMs = 60_000;
  const reportIntervalMs = 15_000;
  let activeSeconds = 0;
  let reportedSeconds = 0;
  let maxScrollDepth = 0;
  let completed = false;
  let completionReported = false;
  let lastActivityAt = Date.now();
  const store = window.ReadingHistoryStore.create({
    authenticated: tracker.dataset.authenticated === "true",
    eventsUrl: tracker.dataset.readingEventsUrl,
    postSlug: tracker.dataset.postSlug,
    title: document.title,
  });
  function markActivity() {
    lastActivityAt = Date.now();
  }
  function updateDepth() {
    const rect = article.getBoundingClientRect();
    const articleHeight = Math.max(article.scrollHeight, rect.height);
    const visible = Math.max(0, Math.min(articleHeight, window.innerHeight - rect.top));
    maxScrollDepth = Math.max(
      maxScrollDepth,
      Math.min(100, articleHeight <= window.innerHeight ? 100 : Math.round((visible * 100) / articleHeight)),
    );
    if (maxScrollDepth >= 80 || (articleHeight <= window.innerHeight && activeSeconds >= 15)) {
      completed = true;
    }
  }
  function report(finalType) {
    const delta = activeSeconds - reportedSeconds;
    if (delta <= 0 && !finalType) {
      return;
    }
    reportedSeconds = activeSeconds;
    if (finalType === "complete") {
      store.recordComplete(delta, maxScrollDepth);
      completionReported = true;
    } else if (finalType === "close") {
      store.recordClose(delta, maxScrollDepth);
    } else {
      store.recordHeartbeat(delta, maxScrollDepth);
    }
    renderSummary();
  }
  function renderSummary() {
    const target = document.querySelector(".reading-summary");
    const summary = store.getSummary();
    if (target && summary) {
      target.textContent = `已读 ${summary.readCount} 次 · 累计 ${summary.totalActiveSeconds} 秒 · 最近阅读于 ${summary.lastReadAt}`;
    }
  }
  ["scroll", "touchstart", "click", "keydown"].forEach((type) =>
    window.addEventListener(type, markActivity, { passive: true }),
  );
  setInterval(() => {
    if (!document.hidden && Date.now() - lastActivityAt <= activityWindowMs) {
      activeSeconds += 1;
      updateDepth();
    }
  }, 1000);
  setInterval(() => report(completed && !completionReported ? "complete" : null), reportIntervalMs);
  window.addEventListener("scroll", updateDepth, { passive: true });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      report(null);
    }
  });
  window.addEventListener("pagehide", () => report("close"));
  updateDepth();
  store.recordOpen();
  renderSummary();
})();
