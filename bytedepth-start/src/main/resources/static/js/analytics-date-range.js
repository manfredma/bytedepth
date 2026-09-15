(function (root) {
  'use strict';

  function pad(value) {
    return String(value).padStart(2, '0');
  }

  function format(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }

  function startOfWeek(date) {
    const result = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = result.getDay();
    result.setDate(result.getDate() - (day === 0 ? 6 : day - 1));
    return result;
  }

  function rangeForPeriod(period, now) {
    const date = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    if (period === 'today') { return { from: format(date), to: format(date) }; }
    if (period === 'week') {
      const from = startOfWeek(date);
      const to = new Date(from); to.setDate(to.getDate() + 6);
      return { from: format(from), to: format(to) };
    }
    if (period === 'month') {
      return { from: format(new Date(date.getFullYear(), date.getMonth(), 1)), to: format(new Date(date.getFullYear(), date.getMonth() + 1, 0)) };
    }
    if (period === 'year') {
      return { from: format(new Date(date.getFullYear(), 0, 1)), to: format(new Date(date.getFullYear(), 11, 31)) };
    }
    return { from: '2000-01-01', to: format(date) };
  }

  root.AnalyticsDateRange = { rangeForPeriod };
})(window);
