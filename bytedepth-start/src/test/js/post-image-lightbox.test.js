/** @jest-environment jsdom */
const fs = require('fs');
const path = require('path');
const lightboxJs = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/post-image-lightbox.js'), 'utf-8');
const lightboxCss = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/post-image-lightbox.css'), 'utf-8');

test('article pages load the isolated image lightbox assets', () => {
  const detailTemplate = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/public/posts/detail.html'), 'utf-8');

  expect(detailTemplate).toContain('/css/post-image-lightbox.css');
  expect(detailTemplate).toContain('/js/post-image-lightbox.js');
});

test('clicking an article image opens a closable lightbox preview', () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  eval(lightboxJs);

  const image = document.querySelector('.content img');
  image.click();
  const dialog = document.querySelector('.bd-image-lightbox');
  expect(dialog.open).toBe(true);
  expect(dialog.getAttribute('aria-label')).toBe('图片预览');
  expect(dialog.querySelector('.bd-image-lightbox__image').src).toContain('/images/example.png');
  dialog.querySelector('.bd-image-lightbox__close').click();
  expect(dialog.open).toBe(false);
});

test('clicking an image link opens the lightbox without following its link', () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><a href="/images/original.png"><img src="/images/example.png" alt="示例图"></a></div></article>';
  eval(lightboxJs);

  const event = new MouseEvent('click', { bubbles: true, cancelable: true });
  document.querySelector('.content img').dispatchEvent(event);

  expect(event.defaultPrevented).toBe(true);
  expect(document.querySelector('.bd-image-lightbox').open).toBe(true);
});

test('opening an SVG image adds a white canvas behind its transparent areas', () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/lru-diagram.svg" alt="LRU 图"></div></article>';
  eval(lightboxJs);

  document.querySelector('.content img').click();

  expect(document.querySelector('.bd-image-lightbox__frame').classList.contains('bd-image-lightbox__frame--svg')).toBe(true);
  expect(lightboxCss).toMatch(/\.bd-image-lightbox__frame--svg\s*\{[^}]*background:\s*#fff;/s);
});
