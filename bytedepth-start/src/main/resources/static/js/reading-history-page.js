(function () {
  const page = document.getElementById("reading-history-page");
  if (!page || page.dataset.authenticated === "true") {
    return;
  }
  let items;
  try {
    items = JSON.parse(localStorage.getItem("bytedepth.reading-history.v1") || "{}");
  } catch {
    items = {};
  }
  const entries = Object.values(items).sort((a, b) => String(b.lastReadAt).localeCompare(String(a.lastReadAt)));
  const list = page.querySelector(".reading-history-list");
  const empty = page.querySelector(".reading-history-empty");
  if (!list) {
    return;
  }
  list.innerHTML = "";
  entries.slice(0, 1000).forEach((entry) => {
    const link = document.createElement("a");
    link.className = "reading-history-item";
    link.href = `/posts/${encodeURIComponent(entry.postSlug)}`;
    link.innerHTML = `<span class="reading-history-title"></span><span class="reading-history-meta"></span>`;
    link.querySelector(".reading-history-title").textContent = entry.titleSnapshot || entry.postSlug;
    link.querySelector(".reading-history-meta").textContent = `${entry.readCount} 次 · ${entry.totalActiveSeconds} 秒`;
    list.appendChild(link);
  });
  if (empty) {
    empty.hidden = entries.length > 0;
  }
})();
