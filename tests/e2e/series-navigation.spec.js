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
        await expect(page.locator('.series-context-progress')).toContainText(/第 \d+ 篇 · 共 \d+ 篇/);
        const selector = page.locator('.series-selector');
        await expect(selector).toBeVisible();
        await selector.locator('summary').click();
        await expect(selector.locator('.series-selector-item').first()).toBeVisible({timeout: 10_000});
        await expect(selector.locator('[aria-current="page"]')).toHaveCount(1);
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
