import {expect, test} from '@playwright/test';

test.describe('文章图片灯箱', () => {
    test('移动端放大后由灯箱接管拖动并可平移图片', async ({page}, testInfo) => {
        test.skip(testInfo.project.name !== 'mobile-chromium', '仅在移动 Chromium 执行');

        await page.goto('/posts/prototype-pattern', {waitUntil: 'domcontentloaded'});
        const image = page.locator('#post-article .content img').first();
        await expect(image).toBeVisible();
        await image.click();

        const dialog = page.locator('.bd-image-lightbox');
        const frame = dialog.locator('.bd-image-lightbox__frame');
        const preview = dialog.locator('.bd-image-lightbox__image');
        await expect(dialog).toBeVisible();

        await dialog.evaluate(element => {
            element.dispatchEvent(new WheelEvent('wheel', {
                bubbles: true,
                cancelable: true,
                ctrlKey: true,
                deltaY: -70
            }));
        });
        await expect(dialog).toHaveClass(/bd-image-lightbox--zoomed/);
        expect(await frame.evaluate(element => getComputedStyle(element).touchAction)).toBe('none');

        const box = await preview.boundingBox();
        expect(box).not.toBeNull();
        await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
        await page.mouse.down();
        await page.mouse.move(box.x + box.width / 2 + 80, box.y + box.height / 2 + 40);
        await page.mouse.up();

        expect(await preview.evaluate(element => element.style.transform)).toContain('translate(80px, 0px)');
    });
});
