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

async function createDraft(page, title, content) {
    await page.goto('/admin/posts/new', {waitUntil: 'commit'});
    const categoryId = await page.locator('select[name="categoryId"] option').first().getAttribute('value');
    const created = await page.evaluate(async ({title: postTitle, content: postContent, category}) => {
        const csrf = document.querySelector('meta[name="_csrf"]')?.content;
        const body = new URLSearchParams({title: postTitle, content: postContent, categoryId: category, _csrf: csrf});
        const response = await fetch('/admin/posts', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body
        });
        return {ok: response.ok, status: response.status};
    }, {title, content, category: categoryId});
    expect(created.ok, `draft creation failed with HTTP ${created.status}`).toBeTruthy();

    await page.goto(`/admin/posts?title=${encodeURIComponent(title)}`, {waitUntil: 'commit'});
    const card = page.locator('.post-card').filter({hasText: title}).first();
    await expect(card).toBeVisible();
    return {
        editPath: new URL(await card.locator('.btn-edit').getAttribute('href'), page.url()).pathname,
        postPath: new URL(await card.locator('.title').getAttribute('href'), page.url()).pathname
    };
}

async function createAnnotation(page, selectedText, annotationText = null) {
    return page.evaluate(async ({selectedText: expectedText, annotationText: note}) => {
        const content = document.querySelector('#post-article .content');
        const text = content.textContent;
        const startOffset = text.indexOf(expectedText);
        if (startOffset < 0) {
            throw new Error(`Unable to find selected text: ${expectedText}`);
        }
        const csrf = document.querySelector('meta[name="_csrf"]')?.content;
        const response = await fetch(`${window.location.pathname}/annotations`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf},
            body: JSON.stringify({
                selectedText: expectedText,
                annotationText: note,
                color: 'yellow',
                visibility: 'PUBLIC',
                startOffset,
                endOffset: startOffset + expectedText.length
            })
        });
        if (!response.ok) {
            throw new Error(`annotation creation failed with HTTP ${response.status}`);
        }
        return response.json();
    }, {selectedText, annotationText});
}

async function updateDraft(page, editPath, title, content) {
    await page.goto(editPath, {waitUntil: 'commit'});
    const categoryId = await page.locator('select[name="categoryId"] option:checked').getAttribute('value');
    const updatePath = await page.locator('form.post-editor-form').getAttribute('action');
    expect(updatePath).toBeTruthy();
    const updated = await page.evaluate(async ({title: postTitle, content: postContent, category, path}) => {
        const csrf = document.querySelector('meta[name="_csrf"]')?.content;
        const body = new URLSearchParams({title: postTitle, content: postContent, categoryId: category, _csrf: csrf});
        const response = await fetch(path, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body
        });
        return {ok: response.ok, status: response.status};
    }, {title, content, category: categoryId, path: updatePath});
    expect(updated.ok, `draft update failed with HTTP ${updated.status}`).toBeTruthy();
}

async function removeAnnotation(page, postPath, id) {
    await page.evaluate(async ({path, annotationId}) => {
        const csrf = document.querySelector('meta[name="_csrf"]')?.content;
        const response = await fetch(`${path}/annotations/${annotationId}`, {
            method: 'DELETE', headers: {'X-CSRF-TOKEN': csrf}
        });
        if (response.status !== 204) {
            throw new Error(`annotation cleanup failed with HTTP ${response.status}`);
        }
    }, {path: postPath, annotationId: id});
}

async function deleteDraft(page, editPath) {
    await page.goto(editPath, {waitUntil: 'commit'});
    const deletePath = editPath.replace(/\/edit$/, '/delete');
    await page.evaluate(async path => {
        const csrf = document.querySelector('meta[name="_csrf"]')?.content;
        const response = await fetch(path, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({_csrf: csrf})
        });
        if (!response.ok) {
            throw new Error(`draft cleanup failed with HTTP ${response.status}`);
        }
    }, deletePath);
}

test.describe('文章更新后的划线锚点', () => {
    test('新增段落后保留存活评注，删除正文后隐藏已失效划线', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '仅在桌面 Chromium 执行');
        test.skip(!adminUsername || !adminPassword,
            '需要 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD 才能执行真实文章更新 E2E');

        await loginAsAdmin(page);
        const title = `annotation-content-e2e-${Date.now()}`;
        const oldContent = '第一段\n\n目标句子\n\n旧方案\n\n待删除内容';
        const newContent = '第一段\n\n新增段\n\n目标句子\n\n新方案';
        const paths = await createDraft(page, title, oldContent);
        let liveAnnotation;
        let replacedAnnotation;
        let deletedAnnotation;
        try {
            await page.goto(paths.postPath, {waitUntil: 'commit'});
            await expect(page.locator('#post-article[data-bd-annotation-ready="true"]')).toBeVisible({timeout: 15_000});
            liveAnnotation = await createAnnotation(page, '目标句子', '更新后仍然存在的评注');
            replacedAnnotation = await createAnnotation(page, '旧方案', '被替换的旧方案评注');
            deletedAnnotation = await createAnnotation(page, '待删除内容');

            await updateDraft(page, paths.editPath, title, newContent);
            await page.goto(paths.postPath, {waitUntil: 'commit'});
            await expect(page.locator('#post-article[data-bd-annotation-ready="true"]')).toBeVisible({timeout: 15_000});

            await expect(page.locator(`mark[data-id="${liveAnnotation.id}"]`).filter({hasText: '目标句子'})).toBeVisible();
            await expect(page.locator(`mark[data-id="${replacedAnnotation.id}"]`)).toHaveCount(0);
            await expect(page.locator(`mark[data-id="${deletedAnnotation.id}"]`)).toHaveCount(0);
            const sidebar = page.locator('#bd-annotation-sidebar');
            if (!await sidebar.evaluate(element => element.classList.contains('bd-annotation-sidebar-open'))) {
                await page.locator('#bd-annotation-sidebar-toggle').click();
            }
            await expect(page.locator(`.bd-annotation-feed-item[data-id="${liveAnnotation.id}"]`)).toContainText('更新后仍然存在的评注');
            await expect(page.locator(`.bd-annotation-feed-item[data-id="${replacedAnnotation.id}"]`)).toHaveCount(0);
            await expect(page.locator(`.bd-annotation-feed-item[data-id="${deletedAnnotation.id}"]`)).toHaveCount(0);
        } finally {
            if (liveAnnotation) {
                await removeAnnotation(page, paths.postPath, liveAnnotation.id);
            }
            if (deletedAnnotation) {
                // 已失效批注不在公开列表中，但删除 API 仍按归属允许清理。
                await removeAnnotation(page, paths.postPath, deletedAnnotation.id);
            }
            if (replacedAnnotation) {
                await removeAnnotation(page, paths.postPath, replacedAnnotation.id);
            }
            await deleteDraft(page, paths.editPath);
        }
    });
});
