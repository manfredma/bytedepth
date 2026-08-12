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

async function createCommentAnnotation(page, annotationText) {
    return page.evaluate(async text => {
        const token = document.querySelector('meta[name="_csrf"]').content;
        const response = await fetch(`${window.location.pathname}/annotations`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': token},
            body: JSON.stringify({
                selectedText: 'He', annotationText: text, color: 'yellow', visibility: 'PUBLIC',
                startOffset: 0, endOffset: 2
            })
        });
        if (!response.ok) {
            throw new Error(`annotation setup failed: ${response.status}`);
        }
        return response.json();
    }, annotationText);
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
            await page.locator(`mark[data-id="${saved.id}"] .bd-annotation-comment-trigger`).click();
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
            await expect(page.locator(`mark[data-id="${first.id}"]`)).toBeVisible();

            await selectArticleText(page, 2, 2);
            await popup.getByRole('button', {name: '划线'}).click();
            const secondResponse = page.waitForResponse(response => response.url().endsWith('/annotations')
                && response.request().method() === 'POST');
            await popup.getByRole('button', {name: '珊瑚色直线'}).click();
            const second = await (await secondResponse).json();
            createdIds.push(second.id);
            await expect(page.locator(`mark[data-id="${first.id}"] mark[data-id="${second.id}"]`)).toBeVisible();

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
});
