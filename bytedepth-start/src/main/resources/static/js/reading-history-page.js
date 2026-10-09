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
  const entries = Object.values(items)
    .sort((a, b) => String(b.lastReadAt).localeCompare(String(a.lastReadAt)))
    .slice(0, 1000);
  const list = page.querySelector(".reading-history-list");
  const empty = page.querySelector(".reading-history-empty");
  if (!list) {
    return;
  }
  const slugs = entries.map((entry) => entry.postSlug).filter(Boolean);
  const query = new URLSearchParams();
  slugs.slice(0, 20).forEach((slug) => query.append("slugs", slug));
  fetch(`/reading-history/available-posts?${query.toString()}`, { credentials: "same-origin" })
    .then((response) => (response.ok ? response.json() : []))
    .then((available) => {
      const visible = new Map(available.map((post) => [post.slug, post]));
      list.innerHTML = "";
      entries
        .filter((entry) => visible.has(entry.postSlug))
        .forEach((entry) => {
          const post = visible.get(entry.postSlug);
          const link = document.createElement("a");
          link.className = "reading-history-item";
          link.href = post.path;
          link.innerHTML = `<span class="reading-history-title"></span><span class="reading-history-meta"></span>`;
          link.querySelector(".reading-history-title").textContent = post.title;
          link.querySelector(".reading-history-meta").textContent =
            `${entry.readCount} 次 · ${entry.totalActiveSeconds} 秒`;
          list.appendChild(link);
        });
      if (empty) {
        empty.hidden = list.children.length > 0;
      }
    })
    .catch(() => {
      list.innerHTML = "";
      if (empty) {
        empty.hidden = false;
      }
    });
})();
