import {expect, test} from '@playwright/test';

test.describe('专栏文章导航', () => {
    test('文章开头可查看专栏进度并跳转任意文章', async ({page}) => {
        await page.goto('/columns', {waitUntil: 'domcontentloaded'});
        const seriesLink = page.locator('.series-link').first();
        await expect(seriesLink).toBeVisible();
        await seriesLink.click();

        const firstPost = page.locator('.post-card').first();
        await expect(firstPost).toBeVisible();
        await firstPost.click();

        await expect(page.locator('.series-context')).toBeVisible();
        await expect(page.locator('.series-context-progress')).toContainText(/第 \d+ 篇 · 共 \d+ 篇 · \d+%/);
        await expect(page.locator('.meta')).toContainText(/预计阅读 \d+ 分钟/);
        const sidebar = page.locator('#seriesPanel');
        await expect(page.getByRole('button', {name: '打开专栏导航'}).first()).toBeVisible();
        await page.getByRole('button', {name: '打开专栏导航'}).first().click();
        await expect(sidebar).toHaveClass(/open/);
        await expect(sidebar.locator('.series-item').first()).toBeVisible({timeout: 10_000});
        await expect(sidebar.locator('.series-item-reading-time').first()).toContainText(/约 \d+ 分钟/);
        await expect(sidebar.locator('[aria-current="page"]')).toHaveCount(1);
        await expect(sidebar.locator('.series-panel-progress-text')).toContainText(/阅读进度 · 第 \d+ 篇 \/ 共 \d+ 篇 · \d+%/);
    });

    test('侧边栏文章链接执行完整文档导航', async ({page}) => {
        await page.goto('/columns', {waitUntil: 'domcontentloaded'});
        await page.locator('.series-link').first().click();
        await page.locator('.post-card').first().click();

        const sidebar = page.locator('#seriesPanel');
        await page.getByRole('button', {name: '打开专栏导航'}).first().click();
        await expect(sidebar).toHaveClass(/open/);

        const target = sidebar.locator('.series-item:not([aria-current="page"])').first();
        await expect(target).toBeVisible({timeout: 10_000});
        const targetHref = await target.getAttribute('href');
        expect(targetHref).toBeTruthy();
        const targetUrl = new URL(targetHref, page.url());

        await Promise.all([
            page.waitForNavigation({waitUntil: 'domcontentloaded'}),
            target.click()
        ]);

        expect(new URL(page.url()).pathname).toBe(targetUrl.pathname);
        await expect(page.locator('#post-article')).toBeVisible();
        await expect(page.locator('#post-article h1')).toBeVisible();
    });

    test('移动端也能通过专栏入口打开侧边栏', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'mobile-chromium', '仅在移动 Chromium 执行');
        await page.goto('/columns', {waitUntil: 'domcontentloaded'});
        await page.locator('.series-link').first().click();
        await page.locator('.post-card').first().click();

        await expect(page.locator('#seriesTrigger')).toBeVisible();
        await page.locator('#seriesTrigger').click();
        await expect(page.locator('#seriesPanel')).toHaveClass(/open/);
        const panelWidth = await page.locator('#seriesPanel').boundingBox().then(box => box?.width ?? 0);
        expect(panelWidth).toBeGreaterThan(0);
        // CSS 的 300px 上限在移动 Chromium 的设备像素换算中可能出现极小的亚像素误差。
        expect(panelWidth).toBeLessThanOrEqual(300.5);
    });

    test('专栏上下篇导航按设备提供合适的触控尺寸', async ({page}, testInfo) => {
        await page.goto('/columns', {waitUntil: 'domcontentloaded'});
        await page.locator('.series-link').first().click();
        await page.locator('.post-card').first().click();

        const navigation = page.locator('.series-post-nav');
        await expect(navigation).toBeVisible();
        const height = await navigation.boundingBox().then(box => box?.height ?? 0);
        if (testInfo.project.name === 'mobile-chromium') {
            expect(height).toBeGreaterThanOrEqual(150);
        } else {
            expect(height).toBeLessThanOrEqual(110);
        }
    });
});
