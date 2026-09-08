const fs = require('fs');
const path = require('path');

test('shared navigation exposes an accessible RSS link without breaking its mobile boundary', () => {
  const nav = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/fragments/nav.html'), 'utf-8');
  const css = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/nav.css'), 'utf-8');

  expect(nav).toContain('th:href="@{/feed.xml}"');
  expect(nav).toContain('aria-label="订阅 RSS 更新"');
  expect(nav).toContain('aria-hidden="true"');
  expect(css).toContain('.nav-rss');
  expect(css).toContain('@media (max-width:940px)');
});
