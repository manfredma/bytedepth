import { vi } from "vitest";
import fs from "node:fs";
import path from "node:path";
const script = fs.readFileSync(path.resolve(__dirname, "../../main/resources/static/js/post-reading.js"), "utf8");
const storeScript = fs.readFileSync(
  path.resolve(__dirname, "../../main/resources/static/js/reading-history-store.js"),
  "utf8",
);

describe("private article reading", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    document.cookie = "bd_reading_mode=; Max-Age=0; Path=/";
    document.body.innerHTML =
      '<article class="content">Text</article><div id="post-reading-tracker" data-record-reading="false" data-post-slug="java" data-reading-events-url="/posts/java/reading-events"></div>';
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: true, status: 202 })),
    );
    eval(storeScript);
  });
  afterEach(() => {
    document.cookie = "bd_reading_mode=; Max-Age=0; Path=/";
    vi.restoreAllMocks();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    delete window.ReadingHistoryStore;
  });
  test.each(["false", "true"])("does not write or upload history when authenticated=%s", (authenticated) => {
    document.getElementById("post-reading-tracker").dataset.authenticated = authenticated;
    localStorage.setItem("bytedepth.reading-history.v1", JSON.stringify({ old: { readCount: 2 } }));
    eval(script);
    vi.advanceTimersByTime(16000);
    window.dispatchEvent(new Event("pagehide"));
    expect(JSON.parse(localStorage.getItem("bytedepth.reading-history.v1"))).toEqual({ old: { readCount: 2 } });
    expect(localStorage.getItem("bytedepth.reading-events.v1")).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
  });
  test("a normal page does not report private scrolling as completion after exiting", () => {
    const tracker = document.getElementById("post-reading-tracker");
    tracker.dataset.recordReading = "true";
    tracker.dataset.authenticated = "false";
    const article = document.querySelector(".content");
    let top = 0;
    vi.spyOn(article, "getBoundingClientRect").mockImplementation(() => ({ top, height: 1000 }));
    vi.stubGlobal("innerHeight", 500);
    eval(script);
    document.cookie = "bd_reading_mode=private; Path=/";
    top = -400;
    window.dispatchEvent(new Event("scroll"));
    vi.spyOn(document, "hidden", "get").mockReturnValue(true);
    document.cookie = "bd_reading_mode=; Max-Age=0; Path=/";
    vi.advanceTimersByTime(15000);
    const events = JSON.parse(localStorage.getItem("bytedepth.reading-events.v1"));
    expect(events.map((event) => event.type)).toEqual(["READ_OPEN"]);
    expect(events[0].maxScrollDepth).toBe(0);
  });
});
