(function (root) {
  "use strict";

  const number = new Intl.NumberFormat("zh-CN");
  const pad = (value) => String(value).padStart(2, "0");
  const dateText = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

  function rate(current, previous) {
    if (previous === null || current === null) {
      return "—";
    }
    if (previous === 0) {
      return current === 0 ? "0.0%" : "新增";
    }
    const value = ((current - previous) / previous) * 100;
    return `${value > 0 ? "+" : ""}${value.toFixed(1)}%`;
  }

  function bucket(start, index, unit) {
    const [year, month, day] = start.split("-").map(Number);
    if (unit === "month") {
      return new Date(year, month - 1 + index, 1);
    }
    if (unit === "hour") {
      return new Date(year, month - 1, day, index);
    }
    return new Date(year, month - 1, day + index);
  }

  function label(date, unit) {
    if (unit === "month") {
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
    }
    return dateText(date) + (unit === "hour" ? ` ${pad(date.getHours())}:00` : "");
  }

  function build(comparison, now = new Date()) {
    const current = comparison.current || [];
    const previous = comparison.previous || [];
    const start = comparison.currentPeriod.slice(0, 10);
    const unit =
      current.length && current[0].label.includes(":")
        ? "hour"
        : current.length && /^\d{4}-\d{2}$/.test(current[0].label)
          ? "month"
          : "day";
    let total = 0;
    let previousTotal = 0;
    let cumulative = 0;
    let previousCumulative = 0;
    let peak = null;
    let peakTime = "—";
    const rows = current.map((point, index) => {
      const time = bucket(start, index, unit);
      // 月桶以月初命名，但部分月份的所选范围可能尚未开始。
      const elapsed = time <= now && bucket(start, 0, "day") <= now;
      const count = elapsed ? point.viewCount : null;
      const prior = previous[index] ? previous[index].viewCount : null;
      const timeLabel = label(time, unit);
      if (elapsed) {
        total += count;
        previousTotal += prior === null ? 0 : prior;
        cumulative += count;
        if (peak === null || count > peak) {
          peak = count;
          peakTime = timeLabel;
        }
      }
      previousCumulative += prior === null ? 0 : prior;
      return {
        time: timeLabel,
        previousTime: prior === null ? "—" : label(bucket(comparison.previousPeriod.slice(0, 10), index, unit), unit),
        current: count,
        previous: prior,
        change: count === null || prior === null ? null : count - prior,
        rate: rate(count, prior),
        cumulative: elapsed ? cumulative : null,
        previousCumulative: prior === null ? null : previousCumulative,
      };
    });
    rows.forEach((row) => {
      row.share = row.current === null || total === 0 ? "—" : `${((row.current / total) * 100).toFixed(1)}%`;
    });
    const chartValues = rows.map((row) => row.current);
    // 接口补齐的时间桶已按实际时间升序排列；先计算累计再反转，避免跨年标签或小时文字排序失真。
    rows.reverse();
    const priorTotal = previous.length ? previousTotal : null;
    return {
      rows,
      chartValues,
      summary: { current: total, previous: priorTotal, rate: rate(total, priorTotal), peak, peakTime },
    };
  }

  function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined) {
      node.textContent = text;
    }
    if (className) {
      node.className = className;
    }
    return node;
  }

  function countText(value) {
    return value === null ? "—" : number.format(value);
  }

  function render(host, comparison, now = new Date()) {
    const model = build(comparison, now);
    const fragment = document.createDocumentFragment();
    if (!model.rows.length) {
      fragment.append(element("p", "暂无数据", "analytics-trend-empty"));
      host.replaceChildren(fragment);
      return model;
    }
    const summary = element("dl", undefined, "analytics-trend-summary");
    const stats = [
      ["本期累计", countText(model.summary.current)],
      ["上期同期累计", countText(model.summary.previous)],
      ["整体增幅", model.summary.rate],
      ["访问峰值", `${countText(model.summary.peak)} · ${model.summary.peakTime}`],
    ];
    stats.forEach(([name, value]) => {
      const group = element("div");
      group.append(element("dt", name), element("dd", value));
      summary.append(group);
    });
    fragment.append(summary);
    fragment.append(
      element(
        "p",
        `本期：${comparison.currentPeriod}；上期：${comparison.previousPeriod || "无对比周期"}。汇总按本期已开始的时段对齐比较，当前时段尚未结束；未来时段以 — 显示，新增表示上期为零。`,
        "analytics-trend-note",
      ),
    );
    const scroll = element("div", undefined, "analytics-trend-scroll");
    scroll.tabIndex = 0;
    scroll.setAttribute("role", "region");
    scroll.setAttribute("aria-label", "访问趋势明细，可滚动查看");
    const table = element("table");
    const caption = element("caption", "访问趋势明细 · 时间倒序");
    const head = element("thead");
    const headRow = element("tr");
    [
      "时间 ↓",
      "对比时间",
      "本期访问量",
      "上期访问量",
      "增减量",
      "增减幅度",
      "本期占比",
      "本期累计",
      "上期累计",
    ].forEach((title) => {
      const cell = element("th", title);
      cell.scope = "col";
      headRow.append(cell);
    });
    head.append(headRow);
    const body = element("tbody");
    model.rows.forEach((row) => {
      const tr = element("tr");
      const change = row.change === null ? "—" : `${row.change > 0 ? "+" : ""}${number.format(row.change)}`;
      [
        row.time,
        row.previousTime,
        countText(row.current),
        countText(row.previous),
        change,
        row.rate,
        row.share,
        countText(row.cumulative),
        countText(row.previousCumulative),
      ].forEach((value) => tr.append(element("td", value)));
      body.append(tr);
    });
    table.append(caption, head, body);
    scroll.append(table);
    fragment.append(scroll);
    host.replaceChildren(fragment);
    return model;
  }

  root.AnalyticsTrendDetails = { build, render };
})(window);
