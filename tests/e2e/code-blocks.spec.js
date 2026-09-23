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
    test('所有顶层代码块统一渲染并保持各自交互状态', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium', '代码块交互验收在桌面 Chromium 执行');
        test.skip(!adminUsername || !adminPassword,
            '需要 E2E_ADMIN_USERNAME/E2E_ADMIN_PASSWORD 才能执行真实文章 E2E');

        await loginAsAdmin(page);
        const title = `code-block-e2e-${Date.now()}`;
        const content = [
            '未标注语言代码：',
            '',
            '```',
            'unmarked code',
            '```',
            '',
            '标注语言代码：',
            '',
            '```text',
            'plain ordinary code',
            '```',
            '',
            '```java title:Example.java fold tabs:install',
            'System.out.println("java");',
            '```',
            '',
            '```python title:Example.py tabs:install',
            'print("python")',
            '```',
            '',
            'Obsidian Codeblock Customizer 格式：',
            '',
            '```java group:interpreter tab:Java title:"Rule.java"',
            'return evaluateJava();',
            '```',
            '```go group=interpreter tab=Go title=rule.go',
            'return evaluateGo()',
            '```',
            '```python group:interpreter title:rule.py',
            'return evaluate_python()',
            '```'
        ].join('\n');
        const paths = await createDraft(page, title, content);
        try {
            await page.goto(paths.postPath, {waitUntil: 'domcontentloaded'});

            const blocks = page.locator('.bd-code-block');
            await expect(blocks).toHaveCount(7);
            await expect(blocks.first().locator('.bd-code-block__language')).toHaveText('Code');
            await expect(blocks.first().locator('.bd-code-block__line')).toHaveText('1');
            await expect(blocks.first().getByRole('button', {name: '收起代码'})).toBeVisible();
            await expect(blocks.first().locator('pre code')).toHaveText('unmarked code\n');
            await expect(blocks.first().locator('.bd-code-block__title')).toHaveCount(0);

            await expect(blocks.nth(1).locator('.bd-code-block__language')).toHaveText('text');
            await expect(blocks.nth(1).locator('pre code')).toHaveText('plain ordinary code\n');

            const tabs = page.locator('.bd-code-tabs');
            await expect(tabs).toHaveCount(1);
            await expect(tabs.getByRole('tab')).toHaveCount(2);
            const panels = tabs.locator('.bd-code-tabs__panel');
            await expect(panels).toHaveCount(2);
            await expect(panels.first()).toHaveAttribute('aria-hidden', 'false');
            await expect(panels.first().locator('.bd-code-block__body')).toBeHidden();
            await expect(panels.first().locator('.bd-code-block__line')).toHaveText('1');
            await expect(panels.first().getByRole('button', {name: '展开代码'})).toHaveCSS('background-color', /.+/);

            await panels.first().getByRole('button', {name: '展开代码'}).click();
            await expect(panels.first().locator('.bd-code-block__body')).toBeVisible();
            await expect(panels.first().locator('code .token')).not.toHaveCount(0);

            await tabs.getByRole('tab').nth(1).click();
            await expect(panels.nth(0)).toBeHidden();
            await expect(panels.nth(1)).toBeVisible();
            await expect(panels.nth(1).locator('.bd-code-block__title')).toHaveText('Example.py');
            await expect(panels.nth(1).locator('.bd-code-block__line')).toHaveText('1');
            await expect(panels.nth(1).locator('code .token')).not.toHaveCount(0);
            const copyButton = panels.nth(1).getByRole('button', {name: '复制代码'});
            await copyButton.click();
            await expect(copyButton).toHaveText('已复制');

            const customizerTabs = page.locator('.bd-code-tabs').nth(1);
            await expect(customizerTabs.getByRole('tab')).toHaveText(['Java', 'Go', 'Python']);
            const customizerPanels = customizerTabs.locator('.bd-code-tabs__panel');
            await expect(customizerPanels).toHaveCount(3);
            await expect(customizerPanels.first().locator('.bd-code-block__title')).toHaveText('Rule.java');
            await customizerTabs.getByRole('tab', {name: 'Python'}).click();
            await expect(customizerPanels.nth(2)).toBeVisible();
            await expect(customizerPanels.nth(2).locator('.bd-code-block__title')).toHaveText('rule.py');
            await expect(customizerPanels.nth(2).locator('.bd-code-block__line')).toHaveText('1');
        } finally {
            await deleteDraft(page, paths.editPath);
        }
    });
});
