import {expect, test} from '@playwright/test';

const adminUsername = process.env.E2E_ADMIN_USERNAME;
const adminPassword = process.env.E2E_ADMIN_PASSWORD;

async function loginAsAdmin(page) {
    await page.goto('/login', {waitUntil: 'commit'});
    await page.locator('input[name="username"]').fill(adminUsername);
    await page.locator('input[name="password"]').fill(adminPassword);
    await page.getByRole('button', {name: '登录'}).click();
    await expect(page).not.toHaveURL(/\/login/);
}

test.describe('访问统计分析', () => {
    test('国家/地区流量分布接口成功并渲染饼图', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '仅在桌面 Chromium 执行');
        test.skip(!adminUsername || !adminPassword,
            '需要 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD 才能执行后台分析 E2E');

        await loginAsAdmin(page);
        const [response] = await Promise.all([
            page.waitForResponse(candidate =>
                candidate.url().includes('/admin/analytics/api/countries')
                && candidate.request().method() === 'GET'),
            page.goto('/admin/analytics', {waitUntil: 'domcontentloaded'})
        ]);
        expect(response.ok(), `country statistics failed with HTTP ${response.status()}`).toBeTruthy();
        await expect(page.locator('#pie-chart canvas')).toHaveCount(1);
    });
});
