import {expect, test} from '@playwright/test';

const sites = [
    ['ByteDepth', 'https://bytedepth.cn'],
    ['Career', 'https://career.bytedepth.cn'],
    ['Toolbox', 'https://toolbox.bytedepth.cn'],
    ['工作台', 'https://workbench.bytedepth.cn'],
    ['Spring Framework 文档', 'https://docs.spring.io/spring-framework/reference/index.html'],
    ['Java 文档', 'https://docs.oracle.com/en/java/index.html'],
    ['MDN Web Docs', 'https://developer.mozilla.org/en-US/docs/MDN/index.html'],
    ['GitHub Docs', 'https://docs.github.com/en']
];

test('网络地图在各设备显示配置分组、安全外链与响应式卡片', async ({page}, testInfo) => {
    const response = await page.goto('/network');
    expect(response?.ok()).toBeTruthy();

    await expect(page.getByRole('heading', {name: '网络地图'})).toBeVisible();
    await expect(page.getByRole('heading', {name: 'ByteDepth 站点'})).toBeVisible();
    await expect(page.getByRole('heading', {name: '常用技术站点'})).toBeVisible();

    const cards = page.locator('.network-card');
    await expect(cards).toHaveCount(sites.length);

    for (const [index, [name, url]] of sites.entries()) {
        const link = page.getByRole('link', {name: `在新标签页打开：${name}`});
        await expect(link).toHaveAttribute('href', url);
        await expect(cards.nth(index)).toHaveAttribute('target', '_blank');
        await expect(cards.nth(index)).toHaveAttribute('rel', 'noopener noreferrer');
        await expect(cards.nth(index).locator('.network-card-external-icon')).toHaveCount(1);
        await expect(cards.nth(index).locator('.network-card-external-icon')).toHaveAttribute('aria-hidden', 'true');
        await expect(cards.nth(index).locator('.network-card-external-icon')).toHaveAttribute('focusable', 'false');
    }

    const firstGroupColumns = await page.locator('.network-cards').first().evaluate(element =>
        getComputedStyle(element).gridTemplateColumns.trim().split(/\s+/).length
    );
    expect(firstGroupColumns).toBe(testInfo.project.name === 'mobile-chromium' ? 1 : 3);
});

test('staging 显示正式站提示', async ({page}) => {
    await page.goto('/network');

    const notice = page.locator('.network-staging-notice');
    await expect(notice).toContainText('预发环境');
    await expect(notice.getByRole('link', {name: 'bytedepth.cn'}))
        .toHaveAttribute('href', 'https://bytedepth.cn');
    await expect(notice.getByRole('link', {name: 'bytedepth.cn'}))
        .toHaveAttribute('target', '_blank');
    await expect(notice.getByRole('link', {name: 'bytedepth.cn'}))
        .toHaveAttribute('rel', 'noopener noreferrer');
});
