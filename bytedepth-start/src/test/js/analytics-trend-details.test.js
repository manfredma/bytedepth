import { vi } from "vitest";
const fs = require("fs");
const path = require("path");
const points = (labels, counts) => labels.map((label, i) => ({ label, viewCount: counts[i] }));
const fixture = () => ({
  currentPeriod: "2026-12-31 至 2027-01-02",
  previousPeriod: "2026-12-28 至 2026-12-30",
  current: points(["12-31", "01-01", "01-02"], [10, 20, 30]),
  previous: points(["12-31", "01-01", "01-02"], [5, 0, 15]),
});
let details;
beforeEach(async () => {
  vi.resetModules();
  await import("../../main/resources/static/js/analytics-trend-details.js");
  details = window.AnalyticsTrendDetails;
});

test("daily rows descend across years but cumulative counts remain chronological", () => {
  const model = details.build(fixture(), new Date(2027, 0, 3));
  expect(model.rows.map((r) => r.time)).toEqual(["2027-01-02", "2027-01-01", "2026-12-31"]);
  expect(model.rows[0]).toMatchObject({
    previousTime: "2026-12-30",
    current: 30,
    previous: 15,
    change: 15,
    rate: "+100.0%",
    share: "50.0%",
    cumulative: 60,
    previousCumulative: 20,
  });
  expect(model.rows[1].rate).toBe("新增");
  expect(model.summary).toMatchObject({ current: 60, previous: 20, rate: "+200.0%", peak: 30, peakTime: "2027-01-02" });
});

test("hours use numeric chronology and show the associated date", () => {
  const data = {
    currentPeriod: "2026-11-01",
    current: points(
      ["00:00", "01:00", "02:00", "03:00", "04:00", "05:00", "06:00", "07:00", "08:00", "09:00", "10:00"],
      Array(11).fill(1),
    ),
  };
  const model = details.build(data, new Date(2026, 10, 1, 12));
  expect(model.rows[0].time).toBe("2026-11-01 10:00");
  expect(model.rows.at(-1).time).toBe("2026-11-01 00:00");
  expect(model.summary.previous).toBeNull();
  expect(model.rows[0]).toMatchObject({
    previousTime: "—",
    previous: null,
    change: null,
    rate: "—",
    previousCumulative: null,
  });
});

test("month buckets start at their calendar boundary even for partial ranges", () => {
  const model = details.build(
    {
      currentPeriod: "2026-11-15 至 2027-02-20",
      current: points(["2026-11", "2026-12", "2027-01", "2027-02"], [2, 4, 6, 8]),
    },
    new Date(2027, 2, 1),
  );
  expect(model.rows.map((r) => r.time)).toEqual(["2027-02", "2027-01", "2026-12", "2026-11"]);
});

test("future buckets are missing, and summary compares only elapsed buckets", () => {
  const data = fixture();
  const model = details.build(data, new Date(2027, 0, 1, 12));
  expect(model.rows[0]).toMatchObject({ current: null, change: null, rate: "—", cumulative: null, share: "—" });
  expect(model.summary).toMatchObject({ current: 30, previous: 5, rate: "+500.0%", peak: 20 });
  expect(model.chartValues).toEqual([10, 20, null]);
});

test("zero baselines, decreases and empty ranges have explicit output", () => {
  const data = fixture();
  data.current.forEach((p) => (p.viewCount = 0));
  const model = details.build(data, new Date(2027, 0, 3));
  expect(model.rows[1].rate).toBe("0.0%");
  expect(model.rows[0].rate).toBe("-100.0%");
  expect(model.rows[0].share).toBe("—");
  expect(details.build({ currentPeriod: "2026-11-01" }, new Date(2026, 10, 1)).rows).toEqual([]);
  expect(
    details.build({ currentPeriod: "2027-01-01", current: points(["00:00"], [0]) }, new Date(2026, 10, 1)).summary.peak,
  ).toBeNull();
});

test("render replaces old rows, provides nine columns, and handles empty data safely", () => {
  const host = document.createElement("section");
  details.render(host, fixture(), new Date(2027, 0, 3));
  expect(host.querySelectorAll("thead th")).toHaveLength(9);
  expect(host.querySelectorAll("tbody tr")).toHaveLength(3);
  expect(host.querySelector("tbody tr").textContent).toContain("2027-01-02");
  expect(host.textContent).toContain("本期累计");
  details.render(host, { currentPeriod: '<img src=x onerror="alert(1)">' }, new Date());
  expect(host.querySelectorAll("tbody tr")).toHaveLength(0);
  expect(host.textContent).toContain("暂无数据");
  expect(host.querySelector("img")).toBeNull();
});

test("render supports zero baseline, future and missing comparison cells with safe period text", () => {
  const host = document.createElement("section");
  const data = fixture();
  details.render(host, data, new Date(2027, 0, 1));
  expect(host.querySelector("tbody tr").textContent).toContain("—");
  data.current.forEach((point) => (point.viewCount = 0));
  details.render(host, data, new Date(2027, 0, 3));
  expect(host.textContent).toContain("-15");
  details.render(host, { currentPeriod: "2026-11-01", current: points(["00:00"], [2]) });
  expect(host.textContent).toContain("无对比周期");
  expect(host.textContent).toContain("—");
  details.render(host, { currentPeriod: "2026-11-01", current: points(["00:00"], [0]) }, new Date(2026, 9, 31));
  expect(host.querySelectorAll("dd")[3].textContent).toContain("—");
});

test("template refreshes details in the common trend renderer and keeps chart chronology", () => {
  const template = fs.readFileSync(
    path.resolve(__dirname, "../../main/resources/templates/admin/analytics.html"),
    "utf8",
  );
  expect(template).toContain("/css/analytics-trend-details.css");
  expect(template).toContain("/js/analytics-trend-details.js");
  expect(template).toContain("AnalyticsTrendDetails.render(document.getElementById('trend-details'), comparison)");
  expect(template).toContain("data: detailModel.chartValues");
});

test("table leaves scrolling to its region instead of inheriting the admin mobile table reset", () => {
  const css = fs.readFileSync(
    path.resolve(__dirname, "../../main/resources/static/css/analytics-trend-details.css"),
    "utf8",
  );
  document.body.innerHTML =
    "<style>.admin-main table { display: block; overflow-x: auto; max-width: 100%; }</style><style>" +
    css +
    '</style><div class="admin-main"><section class="analytics-trend-details"></section></div>';
  const host = document.querySelector("section");
  details.render(host, fixture(), new Date(2027, 0, 3));
  expect(getComputedStyle(host.querySelector("table")).display).toBe("table");
  expect(getComputedStyle(host.querySelector("table")).overflowX).toBe("visible");
});

test("a future partial-month range stays unobserved until its actual selected start", () => {
  const model = details.build(
    {
      currentPeriod: "2026-11-15 至 2027-02-20",
      previousPeriod: "2026-08-09 至 2026-11-14",
      current: points(["2026-11", "2026-12", "2027-01", "2027-02"], [0, 0, 0, 0]),
      previous: points(["2026-11", "2026-12", "2027-01", "2027-02"], [10, 20, 30, 40]),
    },
    new Date(2026, 10, 1),
  );
  expect(model.chartValues).toEqual([null, null, null, null]);
  expect(model.rows.every((row) => row.current === null && row.rate === "—")).toBe(true);
  expect(model.summary.peak).toBeNull();
});
