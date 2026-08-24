/** @jest-environment jsdom */
const fs = require('fs');
const path = require('path');
const lightboxCss = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/post-image-lightbox.css'), 'utf-8');
const loadLightbox = async () => {
  vi.resetModules();
  await import('../../main/resources/static/js/post-image-lightbox.js');
};
const touchEvent = (type, touches) => {
  const event = new Event(type, { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'touches', { value: touches });
  return event;
};
const gestureEvent = (type, scale = 1) => {
  const event = new Event(type, { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'scale', { value: scale });
  return event;
};

test('article pages load the isolated image lightbox assets', () => {
  const detailTemplate = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/public/posts/detail.html'), 'utf-8');

  expect(detailTemplate).toContain('/css/post-image-lightbox.css');
  expect(detailTemplate).toContain('/js/post-image-lightbox.js');
});

test('lightbox CSS preserves single-finger panning while reserving pinch zoom for the image', () => {
  expect(lightboxCss).toMatch(/\.bd-image-lightbox__frame\s*\{[^}]*touch-action:\s*pan-x pan-y;/s);
});

test('pages without article content do not create a lightbox', async () => {
  document.body.innerHTML = '<main>普通页面</main>';
  await loadLightbox();

  expect(document.querySelector('.bd-image-lightbox')).toBeNull();
});

test('clicking an article image opens a closable lightbox preview', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();

  const image = document.querySelector('.content img');
  image.click();
  const dialog = document.querySelector('.bd-image-lightbox');
  expect(dialog.open).toBe(true);
  expect(dialog.getAttribute('aria-label')).toBe('图片预览');
  expect(dialog.querySelector('.bd-image-lightbox__image').src).toContain('/images/example.png');
  dialog.querySelector('.bd-image-lightbox__close').click();
  expect(dialog.open).toBe(false);
});

test('clicking an image link opens the lightbox without following its link', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><a href="/images/original.png"><img src="/images/example.png" alt="示例图"></a></div></article>';
  await loadLightbox();

  const event = new MouseEvent('click', { bubbles: true, cancelable: true });
  document.querySelector('.content img').dispatchEvent(event);

  expect(event.defaultPrevented).toBe(true);
  expect(document.querySelector('.bd-image-lightbox').open).toBe(true);
});

test('opening an SVG image adds a white canvas behind its transparent areas', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/lru-diagram.svg" alt="LRU 图"></div></article>';
  await loadLightbox();

  document.querySelector('.content img').click();

  expect(document.querySelector('.bd-image-lightbox__frame').classList.contains('bd-image-lightbox__frame--svg')).toBe(true);
  expect(lightboxCss).toMatch(/\.bd-image-lightbox__frame--svg\s*\{[^}]*background:\s*#fff;/s);
});

test('trackpad pinch zooms only the preview and leaves ordinary wheel scrolling alone', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();
  document.querySelector('.content img').click();

  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');
  const pinch = new WheelEvent('wheel', { bubbles: true, cancelable: true, ctrlKey: true, deltaY: -60 });
  dialog.dispatchEvent(pinch);

  expect(pinch.defaultPrevented).toBe(true);
  expect(preview.style.transform).toMatch(/scale\([1-4](?:\.\d+)?\)/);
  expect(preview.style.transform).not.toMatch(/scale\(1\)/);

  const ordinaryWheel = new WheelEvent('wheel', { bubbles: true, cancelable: true, deltaY: -60 });
  dialog.dispatchEvent(ordinaryWheel);
  expect(ordinaryWheel.defaultPrevented).toBe(false);
});

test('two-finger pinch scales the preview instead of the page', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();
  document.querySelector('.content img').click();

  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');
  const start = touchEvent('touchstart', [{ clientX: 0, clientY: 0 }, { clientX: 100, clientY: 0 }]);
  dialog.dispatchEvent(start);
  const move = touchEvent('touchmove', [{ clientX: 0, clientY: 0 }, { clientX: 200, clientY: 0 }]);
  dialog.dispatchEvent(move);

  expect(start.defaultPrevented).toBe(true);
  expect(move.defaultPrevented).toBe(true);
  expect(preview.style.transform).toMatch(/scale\(2\)/);
});

test('Safari gesture zoom is bounded and resets when another image opens', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/first.png" alt="第一张"><img src="/images/second.png" alt="第二张"></div></article>';
  await loadLightbox();
  const images = document.querySelectorAll('.content img');
  images[0].click();

  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');
  const start = gestureEvent('gesturestart');
  dialog.dispatchEvent(start);
  const enlarge = gestureEvent('gesturechange', 10);
  dialog.dispatchEvent(enlarge);

  expect(start.defaultPrevented).toBe(true);
  expect(enlarge.defaultPrevented).toBe(true);
  expect(preview.style.transform).toMatch(/scale\(4\)/);

  dialog.querySelector('.bd-image-lightbox__close').click();
  images[1].click();
  expect(preview.style.transform).toMatch(/scale\(1\)/);
});

test('closed or incomplete gestures never take over page input', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();
  const dialog = document.querySelector('.bd-image-lightbox');
  const twoTouches = [{ clientX: 0, clientY: 0 }, { clientX: 100, clientY: 0 }];

  const closedWheel = new WheelEvent('wheel', { bubbles: true, cancelable: true, ctrlKey: true, deltaY: -60 });
  const closedTouchStart = touchEvent('touchstart', twoTouches);
  const closedTouchMove = touchEvent('touchmove', twoTouches);
  const closedGestureStart = gestureEvent('gesturestart');
  const closedGestureChange = gestureEvent('gesturechange', 2);
  const closedGestureEnd = gestureEvent('gestureend');
  [closedWheel, closedTouchStart, closedTouchMove, closedGestureStart, closedGestureChange, closedGestureEnd]
    .forEach(event => dialog.dispatchEvent(event));

  expect([closedWheel, closedTouchStart, closedTouchMove, closedGestureStart, closedGestureChange, closedGestureEnd]
    .every(event => !event.defaultPrevented)).toBe(true);

  document.querySelector('.content img').click();
  const oneTouchStart = touchEvent('touchstart', [{ clientX: 0, clientY: 0 }]);
  const oneTouchMove = touchEvent('touchmove', [{ clientX: 10, clientY: 0 }]);
  const moveWithoutStart = touchEvent('touchmove', twoTouches);
  const inactiveGestureChange = gestureEvent('gesturechange', 2);
  const inactiveGestureEnd = gestureEvent('gestureend');
  [oneTouchStart, oneTouchMove, moveWithoutStart, inactiveGestureChange, inactiveGestureEnd]
    .forEach(event => dialog.dispatchEvent(event));

  expect([oneTouchStart, oneTouchMove, moveWithoutStart, inactiveGestureChange, inactiveGestureEnd]
    .every(event => !event.defaultPrevented)).toBe(true);
});

test('Safari gesture wins over duplicate touchmove and releases control on gestureend', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();
  document.querySelector('.content img').click();
  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');

  dialog.dispatchEvent(touchEvent('touchstart', [{ clientX: 0, clientY: 0 }, { clientX: 100, clientY: 0 }]));
  dialog.dispatchEvent(gestureEvent('gesturestart'));
  const duplicateMove = touchEvent('touchmove', [{ clientX: 0, clientY: 0 }, { clientX: 400, clientY: 0 }]);
  dialog.dispatchEvent(duplicateMove);
  expect(duplicateMove.defaultPrevented).toBe(true);
  expect(preview.style.transform).toMatch(/scale\(1\)/);

  dialog.dispatchEvent(gestureEvent('gesturechange', 2));
  const end = gestureEvent('gestureend');
  dialog.dispatchEvent(end);
  expect(end.defaultPrevented).toBe(true);
  expect(preview.style.transform).toMatch(/scale\(2\)/);

  const resumedTouchMove = touchEvent('touchmove', [{ clientX: 0, clientY: 0 }, { clientX: 400, clientY: 0 }]);
  dialog.dispatchEvent(resumedTouchMove);
  expect(preview.style.transform).toMatch(/scale\(4\)/);
  dialog.dispatchEvent(touchEvent('touchend', [{ clientX: 0, clientY: 0 }, { clientX: 400, clientY: 0 }]));
  dialog.dispatchEvent(touchEvent('touchend', [{ clientX: 0, clientY: 0 }]));
  const moveAfterEnd = touchEvent('touchmove', [{ clientX: 0, clientY: 0 }, { clientX: 50, clientY: 0 }]);
  dialog.dispatchEvent(moveAfterEnd);
  expect(moveAfterEnd.defaultPrevented).toBe(false);
});

test('keyboard, backdrop, currentSrc, and fallback labels preserve lightbox behavior', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/fallback.png" alt=""></div></article>';
  const image = document.querySelector('.content img');
  Object.defineProperty(image, 'currentSrc', { value: 'data:image/svg+xml,%3Csvg/%3E' });
  await loadLightbox();
  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');

  expect(image.getAttribute('aria-label')).toBe('图片，点击放大');
  const ignoredKey = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
  image.dispatchEvent(ignoredKey);
  expect(ignoredKey.defaultPrevented).toBe(false);
  expect(dialog.open).toBe(false);

  const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
  image.dispatchEvent(enter);
  expect(enter.defaultPrevented).toBe(true);
  expect(preview.src).toContain('data:image/svg+xml');
  expect(preview.alt).toBe('');
  expect(dialog.querySelector('.bd-image-lightbox__frame').classList.contains('bd-image-lightbox__frame--svg')).toBe(true);

  dialog.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  expect(dialog.open).toBe(false);
  const space = new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true });
  image.dispatchEvent(space);
  expect(space.defaultPrevented).toBe(true);
  expect(dialog.open).toBe(true);

  dialog.dispatchEvent(new Event('close'));
  const shrink = new WheelEvent('wheel', { bubbles: true, cancelable: true, ctrlKey: true, deltaY: 1000 });
  dialog.dispatchEvent(shrink);
  expect(preview.style.transform).toMatch(/scale\(1\)/);
});

test('dragging the preview after zoom pans it within the lightbox', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();
  document.querySelector('.content img').click();
  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');

  // 放大到 2 倍后才允许拖动平移。
  dialog.dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true, ctrlKey: true, deltaY: -70 }));
  expect(preview.style.transform).toMatch(/scale\(2/);

  // 鼠标按下后拖动，图片应平移（transform 含 translate）。
  const down = new Event('pointerdown', { bubbles: true, cancelable: true });
  Object.defineProperties(down, { clientX: { value: 100 }, clientY: { value: 100 }, pointerId: { value: 1 } });
  preview.dispatchEvent(down);
  const move = new Event('pointermove', { bubbles: true, cancelable: true });
  Object.defineProperties(move, { clientX: { value: 140 }, clientY: { value: 110 } });
  window.dispatchEvent(move);
  expect(preview.style.transform).toMatch(/translate\(/);
  window.dispatchEvent(new Event('pointerup', { bubbles: true, cancelable: true }));

  // 关闭重开后平移重置。
  dialog.dispatchEvent(new Event('close'));
  expect(preview.style.transform).not.toMatch(/translate\([^0]/);
});

test('pointerdown before zoom does not start panning', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();
  document.querySelector('.content img').click();
  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');

  // 未放大（scale=1）时 pointerdown 不应启动平移。
  const down = new Event('pointerdown', { bubbles: true, cancelable: true });
  Object.defineProperties(down, { clientX: { value: 100 }, clientY: { value: 100 }, pointerId: { value: 1 } });
  preview.dispatchEvent(down);
  const move = new Event('pointermove', { bubbles: true, cancelable: true });
  Object.defineProperties(move, { clientX: { value: 200 }, clientY: { value: 200 } });
  window.dispatchEvent(move);
  expect(preview.style.transform).not.toMatch(/translate\([^0]/);
});

test('second touch pointerdown does not start panning', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();
  document.querySelector('.content img').click();
  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');
  dialog.dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true, ctrlKey: true, deltaY: -70 }));

  // 双指的第二指（pointerType=touch, isPrimary=false）不触发平移。
  const down = new Event('pointerdown', { bubbles: true, cancelable: true });
  Object.defineProperties(down, { clientX: { value: 100 }, clientY: { value: 100 }, pointerId: { value: 2 }, pointerType: { value: 'touch' }, isPrimary: { value: false } });
  preview.dispatchEvent(down);
  const move = new Event('pointermove', { bubbles: true, cancelable: true });
  Object.defineProperties(move, { clientX: { value: 300 }, clientY: { value: 300 } });
  window.dispatchEvent(move);
  expect(preview.style.transform).toMatch(/translate\(0px, 0px\)/);
});

test('pointercancel stops panning', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();
  document.querySelector('.content img').click();
  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');

  // 放大后开始拖动，再用 pointercancel 中断。
  dialog.dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true, ctrlKey: true, deltaY: -70 }));
  const down = new Event('pointerdown', { bubbles: true, cancelable: true });
  Object.defineProperties(down, { clientX: { value: 100 }, clientY: { value: 100 }, pointerId: { value: 1 } });
  preview.dispatchEvent(down);
  window.dispatchEvent(new Event('pointercancel', { bubbles: true, cancelable: true }));
  const moveAfterCancel = new Event('pointermove', { bubbles: true, cancelable: true });
  Object.defineProperties(moveAfterCancel, { clientX: { value: 300 }, clientY: { value: 300 } });
  window.dispatchEvent(moveAfterCancel);
  // pointercancel 后 pointermove 不应再改变平移（translate 仍为拖动起始的 0）。
  expect(preview.style.transform).toMatch(/translate\(0px, 0px\)/);
});

test('setPointerCapture is invoked when available', async () => {
  document.body.innerHTML = '<article id="post-article"><div class="content"><img src="/images/example.png" alt="示例图"></div></article>';
  await loadLightbox();
  document.querySelector('.content img').click();
  const dialog = document.querySelector('.bd-image-lightbox');
  const preview = dialog.querySelector('.bd-image-lightbox__image');
  const capture = vi.fn();
  preview.setPointerCapture = capture;

  dialog.dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true, ctrlKey: true, deltaY: -70 }));
  const down = new Event('pointerdown', { bubbles: true, cancelable: true });
  Object.defineProperties(down, { clientX: { value: 100 }, clientY: { value: 100 }, pointerId: { value: 7 } });
  preview.dispatchEvent(down);

  expect(capture).toHaveBeenCalledWith(7);
});
