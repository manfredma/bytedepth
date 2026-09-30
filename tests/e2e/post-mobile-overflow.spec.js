import {expect, test} from '@playwright/test';

const postSlug = process.env.E2E_POST_SLUG ?? 'hello-bytedepth-3';

test('文章正文中的无分隔英文不会撑宽手机页面和页头', async ({page}, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile-chromium', '仅在移动 Chromium 执行');

    await page.goto(`/posts/${postSlug}`, {waitUntil: 'domcontentloaded'});
    const content = page.locator('#post-article .bd-post-content');
    await expect(content).toBeVisible();

    const metrics = await content.evaluate(element => {
        const probe = document.createElement('p');
        probe.dataset.longWordOverflowProbe = 'true';
        probe.textContent = 'unseparatedenglishletters'.repeat(24);
        element.append(probe);
        window.scrollTo({left: 64, top: 0, behavior: 'instant'});

        return {
            documentWidth: document.documentElement.scrollWidth,
            viewportWidth: document.documentElement.clientWidth,
            horizontalScroll: window.scrollX,
            headerLeft: document.querySelector('.nav-bar').getBoundingClientRect().left,
            wordWrap: getComputedStyle(probe).overflowWrap,
        };
    });

    expect(metrics.documentWidth).toBeLessThanOrEqual(metrics.viewportWidth);
    expect(metrics.horizontalScroll).toBe(0);
    expect(metrics.headerLeft).toBeGreaterThanOrEqual(0);
    expect(metrics.wordWrap).toBe('anywhere');
});
