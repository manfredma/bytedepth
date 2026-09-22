const fs = require('fs');
const path = require('path');

const detailTemplate = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/templates/public/posts/detail.html'),
    'utf-8'
);

test('article navigation keeps native full-page links instead of a page-level DOM interceptor', () => {
    expect(detailTemplate).toContain('class="series-item"');
    expect(detailTemplate).not.toContain('/js/series-navigation.js');
});
