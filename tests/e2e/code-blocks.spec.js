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
    const created = await page.evaluate(async ({postTitle, postContent, category}) => {
        const csrf = document.querySelector('meta[name="_csrf"]')?.content;
        const body = new URLSearchParams({title: postTitle, content: postContent, categoryId: category, _csrf: csrf});
        const response = await fetch('/admin/posts', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body
        });
        return {ok: response.ok, status: response.status};
    }, {postTitle: title, postContent: content, category: categoryId});
    expect(created.ok, `draft creation failed with HTTP ${created.status}`).toBeTruthy();

    await page.goto(`/admin/posts?title=${encodeURIComponent(title)}`, {waitUntil: 'commit'});
    const card = page.locator('.post-card').filter({hasText: title}).first();
    await expect(card).toBeVisible();
    return {
        editPath: new URL(await card.locator('.btn-edit').getAttribute('href'), page.url()).pathname,
        postPath: new URL(await card.locator('.title').getAttribute('href'), page.url()).pathname
    };
}

async function deleteDraft(page, editPath) {
    await page.goto(editPath, {waitUntil: 'commit'});
    const csrf = await page.locator('meta[name="_csrf"]').getAttribute('content');
    const deleted = await page.evaluate(async ({path, token}) => {
        const response = await fetch(path.replace(/\/edit$/, '/delete'), {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({_csrf: token})
        });
        return {ok: response.ok, status: response.status};
    }, {path: editPath, token: csrf});
    expect(deleted.ok, `draft cleanup failed with HTTP ${deleted.status}`).toBeTruthy();
}

test.describe('代码块增强兼容性', () => {
    test('普通代码块保持原样，显式元数据才启用增强交互', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '代码块交互验收在桌面 Chromium 执行');
        test.skip(!adminUsername || !adminPassword,
            '需要 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD 才能执行真实文章 E2E');

        await loginAsAdmin(page);
        const title = `code-block-e2e-${Date.now()}`;
        const content = [
            '普通代码：',
            '',
            '```text',
            'plain ordinary code',
            '```',
            '',
            '```java title:Example.java fold tabs:install',
            'System.out.println("java");',
            '```',
            '',
            '```kotlin title:Example.kt tabs:install',
            'println("kotlin")',
            '```'
        ].join('\n');
        const paths = await createDraft(page, title, content);
        try {
            await page.goto(paths.postPath, {waitUntil: 'domcontentloaded'});

            const ordinaryPre = page.locator('pre').filter({has: page.locator('code.language-text')});
            await expect(ordinaryPre).toHaveCount(1);
            await expect(ordinaryPre.locator('xpath=ancestor::*[contains(concat(" ", normalize-space(@class), " "), " bd-code-block ")]'))
                .toHaveCount(0);

            const tabs = page.locator('.bd-code-tabs');
            await expect(tabs).toHaveCount(1);
            await expect(tabs.getByRole('tab')).toHaveCount(2);
            const panels = tabs.locator('.bd-code-tabs__panel');
            await expect(panels).toHaveCount(2);
            await expect(panels.first()).toHaveAttribute('aria-hidden', 'false');
            await expect(panels.first().locator('.bd-code-block__body')).toBeHidden();

            await panels.first().getByRole('button', {name: '展开代码'}).click();
            await expect(panels.first().locator('.bd-code-block__body')).toBeVisible();

            await tabs.getByRole('tab').nth(1).click();
            await expect(panels.nth(0)).toBeHidden();
            await expect(panels.nth(1)).toBeVisible();
            await expect(panels.nth(1).locator('.bd-code-block__title')).toHaveText('Example.kt');
            const copyButton = panels.nth(1).getByRole('button', {name: '复制代码'});
            await copyButton.click();
            await expect(copyButton).toHaveText('已复制');
        } finally {
            await deleteDraft(page, paths.editPath);
        }
    });
});
