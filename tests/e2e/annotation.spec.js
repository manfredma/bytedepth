import {expect, test} from '@playwright/test';

const postSlug = process.env.E2E_POST_SLUG ?? 'hello-bytedepth-3';
const postPath = `/posts/${postSlug}`;

function captureBrowserErrors(page) {
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => {
        if (message.type() === 'error') {
            errors.push(message.text());
        }
    });
    return errors;
}

async function selectArticleText(page, start, length) {
    await page.locator('#post-article .content').evaluate((content, rangeData) => {
        const walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT);
        let node;
        let consumed = 0;
        let startNode;
        let endNode;
        let startOffset;
        let endOffset;
        while ((node = walker.nextNode())) {
            const end = consumed + node.textContent.length;
            if (!startNode && rangeData.start >= consumed && rangeData.start < end) {
                startNode = node;
                startOffset = rangeData.start - consumed;
            }
            if (startNode && rangeData.start + rangeData.length <= end) {
                endNode = node;
                endOffset = rangeData.start + rangeData.length - consumed;
                break;
            }
            consumed = end;
        }
        if (!startNode || !endNode) {
            throw new Error('unable to select requested article text');
        }
        const range = document.createRange();
        range.setStart(startNode, startOffset);
        range.setEnd(endNode, endOffset);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        document.dispatchEvent(new MouseEvent('mouseup', {bubbles: true}));
        // 浏览器在真实拖选结束后还会派发一次 click；普通正文不能因此立刻关闭菜单。
        content.dispatchEvent(new MouseEvent('click', {bubbles: true}));
    }, {start, length});
}

async function waitForAnnotationReady(page) {
    await expect(page.locator('#post-article[data-bd-annotation-ready="true"]')).toBeVisible();
}

async function removeAnnotation(page, id) {
    await page.evaluate(async annotationId => {
        const token = document.querySelector('meta[name="_csrf"]').content;
        const response = await fetch(`${window.location.pathname}/annotations/${annotationId}`, {
            method: 'DELETE', headers: {'X-CSRF-TOKEN': token}
        });
        if (response.status !== 204) {
            throw new Error(`annotation cleanup failed: ${response.status}`);
        }
    }, id);
}

async function createCommentAnnotation(page, annotationText, startOffset = 0) {
    return page.evaluate(async ({text, start}) => {
        const token = document.querySelector('meta[name="_csrf"]').content;
        const response = await fetch(`${window.location.pathname}/annotations`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': token},
            body: JSON.stringify({
                selectedText: 'He', annotationText: text, color: 'yellow', visibility: 'PUBLIC',
                startOffset: start, endOffset: start + 2
            })
        });
        if (!response.ok) {
            throw new Error(`annotation setup failed: ${response.status}`);
        }
        return response.json();
    }, {text: annotationText, start: startOffset});
}

test.describe('划线评论', () => {
    test('桌面端：拖选后显示两级菜单，并可创建和删除评论', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '仅在桌面 Chromium 执行');
        const errors = captureBrowserErrors(page);
        await page.goto(postPath, {waitUntil: 'commit'});
        await waitForAnnotationReady(page);

        const popup = page.locator('.bd-annotation-popup');
        await selectArticleText(page, 0, 2);
        await expect(popup.getByRole('button', {name: '划线'})).toBeVisible();
        await page.locator('h1').click();
        await expect(popup).not.toHaveClass(/bd-annotation-popup-open/);

        await selectArticleText(page, 0, 2);
        await expect(popup.getByRole('button', {name: '划线'})).toBeVisible();
        await popup.getByRole('button', {name: '划线'}).click();
        await expect(popup.getByRole('button', {name: '琥珀色波浪线'})).toBeVisible();

        await popup.getByRole('button', {name: '返回'}).click();
        await popup.getByRole('button', {name: '评论'}).click();
        await expect(page.locator('#bd-annotation-sidebar')).toHaveClass(/bd-annotation-sidebar-open/);
        const composer = page.locator('.bd-annotation-composer');
        await composer.locator('.bd-annotation-composer-text').fill('Playwright 端到端评论');
        await expect(composer.locator('.bd-annotation-visibility')).toHaveValue('PUBLIC');
        const saveResponse = page.waitForResponse(response => response.url().endsWith('/annotations')
            && response.request().method() === 'POST');
        await composer.getByRole('button', {name: '保存'}).click();
        const saved = await (await saveResponse).json();
        const savedItem = page.locator(`.bd-annotation-feed-item[data-id="${saved.id}"]`);
        try {
            await expect(savedItem.locator('.bd-annotation-feed-text')).toHaveText('Playwright 端到端评论');
            await page.locator('.bd-annotation-sidebar-close').click();
            await page.locator('#bd-annotation-sidebar-toggle').click();
            await expect(page.locator('#bd-annotation-sidebar')).toHaveClass(/bd-annotation-sidebar-open/);
            await savedItem.getByRole('button', {name: '删除'}).click();
            await expect(savedItem).toHaveCount(0);
            expect(errors).toEqual([]);
        } finally {
            if (await savedItem.count()) {
                await removeAnnotation(page, saved.id);
            }
        }
    });

    test('移动端：点击已有评论的划线显示基础评论内容', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'mobile-chromium', '仅在移动 Chromium 执行');
        const errors = captureBrowserErrors(page);
        await page.goto(postPath, {waitUntil: 'commit'});
        await waitForAnnotationReady(page);
        const annotation = await createCommentAnnotation(page, '移动端基础评论');
        try {
            await page.reload({waitUntil: 'commit'});
            await waitForAnnotationReady(page);
            await page.locator(`mark[data-id="${annotation.id}"]`).click();
            await expect(page.locator('.bd-annotation-mobile-note')).toHaveText('移动端基础评论');
            expect(errors).toEqual([]);
        } finally {
            await removeAnnotation(page, annotation.id);
        }
    });

    test('桌面端：侧栏打开时选词仍先显示操作菜单', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '仅在桌面 Chromium 执行');
        const errors = captureBrowserErrors(page);
        await page.goto(postPath, {waitUntil: 'commit'});
        await waitForAnnotationReady(page);

        await page.locator('#bd-annotation-sidebar-toggle').click();
        await expect(page.locator('#bd-annotation-sidebar')).toHaveClass(/bd-annotation-sidebar-open/);
        await selectArticleText(page, 0, 2);

        const popup = page.locator('.bd-annotation-popup');
        await expect(popup.getByRole('button', {name: '划线'})).toBeVisible();
        await expect(page.locator('.bd-annotation-composer')).toBeHidden();
        expect(errors).toEqual([]);
    });

    test('桌面端：已有划线仍可再次划线，并可删除自己的划线', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '仅在桌面 Chromium 执行');
        const errors = captureBrowserErrors(page);
        await page.goto(postPath, {waitUntil: 'commit'});
        await waitForAnnotationReady(page);
        const popup = page.locator('.bd-annotation-popup');
        const createdIds = [];
        try {
            await selectArticleText(page, 0, 4);
            await popup.getByRole('button', {name: '划线'}).click();
            const firstResponse = page.waitForResponse(response => response.url().endsWith('/annotations')
                && response.request().method() === 'POST');
            await popup.getByRole('button', {name: '琥珀色波浪线'}).click();
            const first = await (await firstResponse).json();
            createdIds.push(first.id);
            await expect(page.locator(`mark[data-id="${first.id}"]`).first()).toBeVisible();

            await selectArticleText(page, 2, 2);
            await popup.getByRole('button', {name: '划线'}).click();
            const secondResponse = page.waitForResponse(response => response.url().endsWith('/annotations')
                && response.request().method() === 'POST');
            await popup.getByRole('button', {name: '珊瑚色直线'}).click();
            const second = await (await secondResponse).json();
            createdIds.push(second.id);
            await expect(page.locator(`mark[data-id="${first.id}"] mark[data-id="${second.id}"]`).first()).toBeVisible();

            await page.evaluate(() => window.getSelection().removeAllRanges());
            await page.locator(`mark[data-id="${second.id}"]`).last().click();
            await expect(popup.getByRole('button', {name: '删除划线'})).toBeVisible();
            const deleteResponse = page.waitForResponse(response => response.url().endsWith(`/annotations/${second.id}`)
                && response.request().method() === 'DELETE');
            await popup.getByRole('button', {name: '删除划线'}).click();
            await deleteResponse;
            createdIds.pop();
            await expect(page.locator(`mark[data-id="${second.id}"]`)).toHaveCount(0);
            expect(errors).toEqual([]);
        } finally {
            for (const id of createdIds) {
                await removeAnnotation(page, id);
            }
        }
    });

    test('桌面端：批注栏不覆盖正文，并在宽屏与文章同滚动范围', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '仅在桌面 Chromium 执行');
        await page.setViewportSize({width: 1280, height: 1000});
        await page.goto(postPath, {waitUntil: 'commit'});
        await waitForAnnotationReady(page);
        await page.locator('#bd-annotation-sidebar-toggle').click();

        const normalDesktopLayout = await page.evaluate(() => {
            const content = document.querySelector('.bd-annotation-reading-content').getBoundingClientRect();
            const sidebar = document.querySelector('#bd-annotation-sidebar').getBoundingClientRect();
            return {contentBottom: content.bottom, sidebarTop: sidebar.top};
        });
        expect(normalDesktopLayout.sidebarTop).toBeGreaterThan(normalDesktopLayout.contentBottom);

        await page.setViewportSize({width: 1440, height: 1000});
        await page.reload({waitUntil: 'commit'});
        await waitForAnnotationReady(page);
        await expect(page.locator('#post-article')).toHaveClass(/bd-annotation-reading-layout-open/);
        const wideDesktopLayout = await page.evaluate(() => {
            const content = document.querySelector('.bd-annotation-reading-content').getBoundingClientRect();
            const heading = document.querySelector('.bd-annotation-reading-content h1').getBoundingClientRect();
            const sidebar = document.querySelector('#bd-annotation-sidebar');
            const sidebarRect = sidebar.getBoundingClientRect();
            return {
                contentWidth: content.width,
                contentRight: content.right,
                contentTop: content.top,
                headingTop: heading.top,
                headingBottom: heading.bottom,
                sidebarLeft: sidebarRect.left,
                sidebarTop: sidebarRect.top,
                sidebarBottom: sidebarRect.bottom,
                sidebarPosition: getComputedStyle(sidebar).position
            };
        });
        expect(wideDesktopLayout.contentWidth).toBeGreaterThan(800);
        expect(wideDesktopLayout.contentRight).toBeLessThan(wideDesktopLayout.sidebarLeft);
        expect(wideDesktopLayout.contentTop).toBeLessThanOrEqual(70);
        expect(wideDesktopLayout.headingTop).toBeGreaterThanOrEqual(0);
        expect(wideDesktopLayout.headingBottom).toBeLessThan(120);
        expect(wideDesktopLayout.sidebarTop).toBeGreaterThanOrEqual(0);
        expect(wideDesktopLayout.sidebarTop).toBeLessThanOrEqual(70);
        expect(wideDesktopLayout.sidebarBottom).toBeGreaterThan(0);
        expect(wideDesktopLayout.sidebarPosition).toBe('sticky');
    });

    test('桌面端：反复开关批注栏后评注框仍贴合正文', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '仅在桌面 Chromium 执行');
        await page.setViewportSize({width: 1440, height: 1000});
        await page.goto(postPath, {waitUntil: 'commit'});
        await waitForAnnotationReady(page);
        const annotation = await createCommentAnnotation(page, '切换布局后仍对齐', 8);
        try {
            await page.reload({waitUntil: 'commit'});
            await waitForAnnotationReady(page);
            const outline = page.locator('.bd-annotation-comment-outline').first();
            await expect(outline).toBeVisible();
            await page.locator('#bd-annotation-sidebar-toggle').click();
            await page.locator('.bd-annotation-sidebar-close').click();
            await page.waitForTimeout(50);
            const geometry = await outline.evaluate(element => {
                const rect = element.getBoundingClientRect();
                return {outlineTop: rect.top, textTop: Number(element.dataset.textTop)};
            });
            expect(geometry.textTop - geometry.outlineTop).toBeCloseTo(9, 1);
        } finally {
            await removeAnnotation(page, annotation.id);
        }
    });

    test('桌面端：评注角标随对应划线滚出视口', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '仅在桌面 Chromium 执行');
        await page.setViewportSize({width: 1440, height: 1000});
        await page.goto(postPath, {waitUntil: 'commit'});
        await waitForAnnotationReady(page);
        const annotation = await createCommentAnnotation(page, '随划线离开的评注', 8);
        try {
            await page.reload({waitUntil: 'commit'});
            await waitForAnnotationReady(page);
            const trigger = page.locator('.bd-annotation-comment-outline .bd-annotation-comment-trigger').first();
            const outline = page.locator('.bd-annotation-comment-outline').first();
            await expect(trigger).toBeVisible();
            if (await page.locator('#bd-annotation-sidebar').evaluate(sidebar => sidebar.classList.contains('bd-annotation-sidebar-open'))) {
                await page.locator('.bd-annotation-sidebar-close').click();
            }
            await trigger.click();
            await expect(page.locator('#bd-annotation-sidebar')).toHaveClass(/bd-annotation-sidebar-open/);
            const feedItem = page.locator(`.bd-annotation-feed-item[data-id="${annotation.id}"]`);
            await expect(feedItem).toBeVisible();
            const attachedPosition = await page.evaluate(([triggerElement, outlineElement]) => {
                const triggerRect = triggerElement.getBoundingClientRect();
                const outlineRect = outlineElement.getBoundingClientRect();
                return {triggerTop: triggerRect.top, triggerBottom: triggerRect.bottom, outlineTop: outlineRect.top, outlineBottom: outlineRect.bottom, textTop: Number(outlineElement.dataset.textTop)};
            }, await Promise.all([trigger.elementHandle(), outline.elementHandle()]));
            expect(Math.abs((attachedPosition.triggerTop + attachedPosition.triggerBottom) / 2 - attachedPosition.outlineTop)).toBeLessThanOrEqual(1);
            expect(attachedPosition.triggerBottom).toBeLessThanOrEqual(attachedPosition.textTop);
            await page.evaluate(() => {
                const spacer = document.createElement('div');
                spacer.setAttribute('aria-hidden', 'true');
                spacer.style.height = '1600px';
                document.querySelector('.bd-annotation-reading-content').append(spacer);
            });
            await page.evaluate(() => window.scrollTo(0, 500));
            await page.waitForTimeout(50);
            expect(await trigger.evaluate(element => element.getBoundingClientRect().bottom)).toBeLessThan(0);
            await expect(feedItem).toBeHidden();
        } finally {
            await removeAnnotation(page, annotation.id);
        }
    });
});
