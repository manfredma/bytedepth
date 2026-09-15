/** @jest-environment jsdom */
const fs = require('fs');
const path = require('path');

const source = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/analytics-date-range.js'),
  'utf-8'
);
const template = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/templates/admin/analytics.html'),
  'utf-8'
);

describe('analytics date range', () => {
  beforeEach(() => {
    window.AnalyticsDateRange = undefined;
    eval(source);
  });

  test('today resolves to the complete local calendar day', () => {
    expect(window.AnalyticsDateRange.rangeForPeriod('today', new Date(2026, 8, 15)))
      .toEqual({ from: '2026-09-15', to: '2026-09-15' });
  });

  test('week, month and year resolve to natural calendar boundaries', () => {
    const date = new Date(2026, 8, 15);
    expect(window.AnalyticsDateRange.rangeForPeriod('week', date))
      .toEqual({ from: '2026-09-14', to: '2026-09-20' });
    expect(window.AnalyticsDateRange.rangeForPeriod('month', date))
      .toEqual({ from: '2026-09-01', to: '2026-09-30' });
    expect(window.AnalyticsDateRange.rangeForPeriod('year', date))
      .toEqual({ from: '2026-01-01', to: '2026-12-31' });
  });

  test('week starts on Monday when today is Sunday', () => {
    expect(window.AnalyticsDateRange.rangeForPeriod('week', new Date(2026, 8, 20)))
      .toEqual({ from: '2026-09-14', to: '2026-09-20' });
  });

  test('all resolves to an explicit range ending today', () => {
    expect(window.AnalyticsDateRange.rangeForPeriod('all', new Date(2026, 8, 15)))
      .toEqual({ from: '2000-01-01', to: '2026-09-15' });
  });

  test('analytics template uses one custom range trigger instead of native date inputs', () => {
    expect(template).toContain('id="date-range-trigger"');
    expect(template).toContain('id="date-range-popover"');
    expect(template).toContain('class="time-controls"');
    expect(template).not.toContain('date-range-label">时间范围');
    expect(template).toContain('positionDatePicker');
    expect(template).not.toContain('type="date"');
  });
});
