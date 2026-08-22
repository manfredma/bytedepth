/** @jest-environment jsdom */
const fs = require('fs');
const path = require('path');
const annotationJs = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/annotation.js'), 'utf-8');
const annotationCss = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/annotation.css'), 'utf-8');

describe('annotation sidebar', () => {
  beforeEach(() => {
    localStorage.clear();
    window.matchMedia = jest.fn(() => ({ matches: false }));
    Range.prototype.getClientRects = () => [{left: 10, right: 90, top: 20, bottom: 40, width: 80, height: 20}];
    Range.prototype.getBoundingClientRect = () => ({left: 10, right: 90, top: 20, bottom: 40, width: 80, height: 20});
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    window.__ANNOTATIONS__ = [];
    window.fetch = jest.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({ id: 1, selectedText: '可批注', color: 'yellow', visibility: 'PRIVATE', startOffset: 0, endOffset: 3, ownedByCurrentVisitor: true }) }));
    eval(annotationJs);
  });
  afterEach(() => {
    delete window.__ANNOTATIONS__;
    delete window.fetch;
    delete navigator.clipboard;
    delete document.execCommand;
  });

  test('sidebar is closed on first visit and toggling remains a per-session choice', () => {
    // 默认关闭：不再读取 localStorage 偏好，首屏阅读区保持完整。
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(false);
    expect(document.querySelector('#post-article').classList.contains('bd-annotation-reading-layout-open')).toBe(false);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(document.querySelector('#post-article').classList.contains('bd-annotation-reading-layout-open')).toBe(true);
    document.querySelector('.bd-annotation-sidebar-close').click();
    expect(document.querySelector('#post-article').classList.contains('bd-annotation-reading-layout-open')).toBe(false);
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(false);
  });

  test('a persisted open preference in localStorage no longer auto-opens the sidebar', () => {
    // 即使历史遗留了「打开」偏好，初始化也保持关闭，不再自动展开。
    localStorage.setItem('bd.annotation.sidebar.open', 'true');
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);
    // 初始化 setOpen(false)：侧栏关闭，且不再回写 localStorage（若回写会变成 'false'）。
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(false);
    expect(localStorage.getItem('bd.annotation.sidebar.open')).toBe('true');
    // 主动打开后也不再回写 localStorage（若回写会保持 'true'，此处仍验证原值不被改写）。
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(localStorage.getItem('bd.annotation.sidebar.open')).toBe('true');
    // 关闭同样不回写：先置 'true'，关闭后验证未被改成 'false'。
    document.querySelector('.bd-annotation-sidebar-close').click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(false);
    expect(localStorage.getItem('bd.annotation.sidebar.open')).toBe('true');
  });

  test('toggle remains usable when browser storage is unavailable', () => {
    const originalGet = Storage.prototype.getItem;
    const originalSet = Storage.prototype.setItem;
    Storage.prototype.getItem = jest.fn(() => { throw new DOMException('blocked', 'SecurityError'); });
    Storage.prototype.setItem = jest.fn(() => { throw new DOMException('blocked', 'SecurityError'); });
    document.body.innerHTML = `<article id="post-article"><button id="bd-annotation-sidebar-toggle"></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section><section class="bd-annotation-feed"></section></aside></article>`;
    expect(() => eval(annotationJs)).not.toThrow();
    // 存储不可用时首次访问仍保持关闭，且开关可正常使用。
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(false);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(false);
    Storage.prototype.getItem = originalGet;
    Storage.prototype.setItem = originalSet;
  });

  test('typing a comment defaults visibility to public while a blank highlight stays private', () => {
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3); range.getBoundingClientRect = () => ({left: 10, bottom: 20});
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    document.querySelector('.bd-annotation-popup [data-comment]').click();
    const visibility = document.querySelector('.bd-annotation-visibility');
    expect(visibility.value).toBe('PRIVATE');
    const input = document.querySelector('.bd-annotation-composer-text'); input.value = '我的评论'; input.dispatchEvent(new Event('input', { bubbles: true }));
    expect(visibility.value).toBe('PUBLIC');
  });

  test('opens a two-level menu before posting a private pure highlight even when the sidebar is open', async () => {
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3); range.getBoundingClientRect = () => ({left: 10, bottom: 20});
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    expect(document.querySelector('.bd-annotation-popup [data-highlight]')).not.toBeNull();
    expect(document.querySelector('.bd-annotation-composer').hidden).toBe(true);
    document.querySelector('.bd-annotation-popup [data-highlight]').click();
    expect(document.querySelector('.bd-annotation-popup [data-back]')).not.toBeNull();
    document.querySelector('.bd-annotation-popup [data-color="yellow"]').click();
    await Promise.resolve();
    expect(window.fetch).toHaveBeenCalledWith(expect.stringContaining('/annotations'), expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(window.fetch.mock.calls[0][1].body)).toMatchObject({ annotationText: null, visibility: 'PRIVATE' });
  });

  test('copies the selected text through the native clipboard and reports success', async () => {
    const writeText = jest.fn(() => Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3); range.getBoundingClientRect = () => ({left: 10, bottom: 20});
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));

    document.querySelector('.bd-annotation-popup [data-copy]').click();
    await Promise.resolve(); await Promise.resolve(); await Promise.resolve();

    expect(writeText).toHaveBeenCalledWith('可批注');
    expect(document.querySelector('.bd-annotation-copy-result').textContent).toBe('已复制到剪贴板');
  });

  test('falls back to execCommand when the native clipboard rejects', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: jest.fn(() => Promise.reject(new Error('blocked'))) }
    });
    document.execCommand = jest.fn(() => true);
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3); range.getBoundingClientRect = () => ({left: 10, bottom: 20});
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));

    document.querySelector('.bd-annotation-popup [data-copy]').click();
    await Promise.resolve(); await Promise.resolve(); await Promise.resolve();

    expect(document.execCommand).toHaveBeenCalledWith('copy');
    expect(document.querySelector('.bd-annotation-copy-result').textContent).toBe('已复制到剪贴板');
  });

  test('uses execCommand when the native clipboard is unavailable', async () => {
    document.execCommand = jest.fn(() => true);
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3); range.getBoundingClientRect = () => ({left: 10, bottom: 20});
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));

    document.querySelector('.bd-annotation-popup [data-copy]').click();
    await Promise.resolve(); await Promise.resolve();

    expect(document.execCommand).toHaveBeenCalledWith('copy');
    expect(document.querySelector('.bd-annotation-copy-result').textContent).toBe('已复制到剪贴板');
  });

  test('mobile selection does not open the desktop annotation menu', () => {
    window.matchMedia = jest.fn(() => ({ matches: true }));
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3);
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    expect(document.querySelector('.bd-annotation-popup')).toBeNull();
  });

  test('only comments contribute to the reading toolbar badge', async () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批', annotationText: '公开评论', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 },
      { id: 3, selectedText: '可批', annotationText: '同一位置的另一条评论', color: 'red', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 },
      { id: 2, selectedText: '注文', annotationText: null, color: 'green', visibility: 'PRIVATE', startOffset: 2, endOffset: 4, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;

    eval(annotationJs);

    expect(document.querySelector('.bd-annotation-comment-count').textContent).toBe('2');
    expect(document.querySelector('.bd-annotation-toolbar-count').textContent).toBe('2');
    expect(document.querySelector('.bd-annotation-toolbar-count').hidden).toBe(false);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelectorAll('.bd-annotation-feed-item')).toHaveLength(2);
    expect(document.querySelectorAll('.bd-annotation-feed-footer')).toHaveLength(2);
    expect(document.querySelector('.bd-annotation-feed-footer').textContent).toContain('公开评论');
    expect(document.querySelector('.bd-annotation-feed-item').dataset.color).toBe('yellow');
    expect(document.querySelector('mark[data-id="1"]').classList.contains('bd-annotation-has-comment')).toBe(true);
    expect(document.querySelector('mark[data-id="2"]').classList.contains('bd-annotation-has-comment')).toBe(false);
    expect(document.querySelector('.bd-annotation-comment-outline .bd-annotation-comment-trigger').textContent).toBe('评注');
    expect(document.querySelector('.bd-annotation-comment-trigger').classList.contains('bd-annotation-comment-trigger-below')).toBe(false);
    expect(document.querySelectorAll('.bd-annotation-comment-trigger')).toHaveLength(1);

    document.querySelector('.bd-annotation-sidebar-close').click();
    const commentTrigger = document.querySelector('.bd-annotation-comment-outline .bd-annotation-comment-trigger');
    commentTrigger.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    commentTrigger.click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(document.querySelector('.bd-annotation-popup')).toBeNull();
    // 再次点击同一 trigger：取消选中，但侧栏保持打开（与点击卡片行为一致）。
    commentTrigger.click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(document.querySelector('.bd-annotation-feed-item-active')).toBeNull();

    window.getSelection().removeAllRanges();
    document.querySelector('mark[data-id="2"]').click();
    expect(document.querySelector('.bd-annotation-popup [data-delete-annotation]')).not.toBeNull();
    document.querySelector('.bd-annotation-popup [data-delete-annotation]').click();
    await Promise.resolve();
    expect(window.fetch).toHaveBeenCalledWith(expect.stringContaining('/annotations/2'), expect.objectContaining({ method: 'DELETE' }));

    document.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(document.querySelector('.bd-annotation-popup').classList.contains('bd-annotation-popup-open')).toBe(false);
  });

  test('clicking the same annotation trigger twice deselects but a different one switches the focus', async () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批', annotationText: '第一条评论', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 },
      { id: 3, selectedText: '注文', annotationText: '第二条评论', color: 'red', visibility: 'PUBLIC', startOffset: 2, endOffset: 4 }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    const triggers = document.querySelectorAll('.bd-annotation-comment-trigger');
    expect(triggers).toHaveLength(2);
    const [triggerA, triggerB] = triggers;

    // 第一次点击：打开侧栏并高亮第一条。
    triggerA.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    triggerA.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(document.querySelector('.bd-annotation-feed-item[data-id="1"]').classList.contains('bd-annotation-feed-item-active')).toBe(true);
    expect(document.querySelector('.bd-annotation-feed-item[data-id="3"]').classList.contains('bd-annotation-feed-item-active')).toBe(false);

    // 点击不同的第二条：侧栏保持打开，高亮切换到第二条。
    triggerB.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    triggerB.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(document.querySelector('.bd-annotation-feed-item[data-id="3"]').classList.contains('bd-annotation-feed-item-active')).toBe(true);
    expect(document.querySelector('.bd-annotation-feed-item[data-id="1"]').classList.contains('bd-annotation-feed-item-active')).toBe(false);

    // 再次点击同一条：取消选中，高亮清零，但侧栏保持打开（与点击卡片行为一致）。
    triggerB.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    triggerB.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(document.querySelectorAll('.bd-annotation-feed-item-active')).toHaveLength(0);
  });

  test('keeps feed items visible in non-sticky mid-width layout', async () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批', annotationText: '评论', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    // jsdom 默认 getComputedStyle 返回空串，sidebar.position !== 'sticky'，
    // layoutFeed 走非 sticky 提前返回分支：卡片应展开为常规列表而非被离场逻辑隐藏。
    eval(annotationJs);

    document.querySelector('#bd-annotation-sidebar-toggle').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    const item = document.querySelector('.bd-annotation-feed-item');
    expect(item).not.toBeNull();
    expect(item.hidden).toBe(false);
    expect(item.style.opacity).toBe('');
  });

  test('defaults to follow layout and switching to compact clears absolute positioning', async () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批', annotationText: '评论', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><div class="bd-annotation-layout-switch" role="group"><button type="button" data-bd-layout="follow" aria-pressed="true">跟随</button><button type="button" data-bd-layout="compact" aria-pressed="false">紧凑</button></div><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    // 默认跟随型：侧栏带 follow class，切换按钮 aria-pressed 正确。
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-feed-follow')).toBe(true);
    expect(document.querySelector('[data-bd-layout="follow"]').getAttribute('aria-pressed')).toBe('true');
    expect(document.querySelector('[data-bd-layout="compact"]').getAttribute('aria-pressed')).toBe('false');

    document.querySelector('#bd-annotation-sidebar-toggle').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    // jsdom 下 sidebar 非 fixed，跟随型不实际定位（走重置分支），但 feedLayout 状态仍是 follow。
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-feed-follow')).toBe(true);

    // 切换到紧凑型：follow class 移除、compact class 加上、aria-pressed 翻转。
    document.querySelector('[data-bd-layout="compact"]').click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-feed-follow')).toBe(false);
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-feed-compact')).toBe(true);
    expect(document.querySelector('[data-bd-layout="follow"]').getAttribute('aria-pressed')).toBe('false');
    expect(document.querySelector('[data-bd-layout="compact"]').getAttribute('aria-pressed')).toBe('true');

    // 切回跟随型。
    document.querySelector('[data-bd-layout="follow"]').click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-feed-follow')).toBe(true);
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-feed-compact')).toBe(false);
  });

  test('clicking a feed card focuses the annotation, scrolls the article mark, and toggles off on reclick', async () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批', annotationText: '第一条', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><div class="bd-annotation-layout-switch" role="group"><button type="button" data-bd-layout="follow" aria-pressed="true">跟随</button><button type="button" data-bd-layout="compact" aria-pressed="false">紧凑</button></div><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    const mark = document.querySelector('mark[data-id="1"]');
    const scrollIntoView = jest.fn();
    mark.scrollIntoView = scrollIntoView;

    // 点击卡片：选中该批注，正文 mark 滚动到视口中心，卡片与正文划线框变深色选中。
    document.querySelector('.bd-annotation-feed-item').click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('.bd-annotation-feed-item').classList.contains('bd-annotation-feed-item-active')).toBe(true);
    expect(document.querySelector('.bd-annotation-comment-outline-active')).not.toBeNull();
    expect(scrollIntoView).toHaveBeenCalledWith({block: 'center', behavior: 'smooth'});

    // 再次点击同一卡片：取消选中，深色态清零。
    document.querySelector('.bd-annotation-feed-item').click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('.bd-annotation-feed-item').classList.contains('bd-annotation-feed-item-active')).toBe(false);
    expect(document.querySelector('.bd-annotation-comment-outline-active')).toBeNull();
  });

  test('clicking the in-article comment trigger and the sidebar card stay in sync', async () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批', annotationText: '评论', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><div class="bd-annotation-layout-switch" role="group"><button type="button" data-bd-layout="follow" aria-pressed="true">跟随</button><button type="button" data-bd-layout="compact" aria-pressed="false">紧凑</button></div><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    // 点击正文「评注」标签：侧栏打开 + 卡片选中。
    const trigger = document.querySelector('.bd-annotation-comment-trigger');
    trigger.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    trigger.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(document.querySelector('.bd-annotation-feed-item-active')).not.toBeNull();
    expect(document.querySelector('.bd-annotation-comment-outline-active')).not.toBeNull();

    // 再次点击正文标签：取消选中。
    trigger.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    trigger.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('.bd-annotation-feed-item-active')).toBeNull();
    expect(document.querySelector('.bd-annotation-comment-outline-active')).toBeNull();
  });

  test('reports failure on the delete button inside the sidebar card', async () => {
    window.__ANNOTATIONS__ = [
      { id: 8, selectedText: '可批', annotationText: '评注', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);
    document.querySelector('#bd-annotation-sidebar-toggle').click();

    const deleteButton = document.querySelector('.bd-annotation-feed-actions button:last-child');
    expect(deleteButton.textContent).toBe('删除');
    window.confirm = jest.fn(() => false);
    deleteButton.click();
    expect(window.fetch).not.toHaveBeenCalled();
    window.confirm = jest.fn(() => true);
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 500 }));
    deleteButton.click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(window.fetch).toHaveBeenCalledWith(expect.stringContaining('/annotations/8'), expect.objectContaining({ method: 'DELETE' }));
    expect(window.confirm).toHaveBeenCalledWith('确定删除这条评注吗？');
    // 失败时卡片保留，按钮文案变为失败提示。
    expect(document.querySelector('.bd-annotation-feed-item[data-id="8"]')).not.toBeNull();
    expect(deleteButton.textContent).toBe('删除失败，请重试');
  });

  test('edits an owned comment in place instead of opening the sidebar composer', async () => {
    window.__ANNOTATIONS__ = [
      { id: 8, selectedText: '可批', annotationText: '原评注', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);
    document.querySelector('#bd-annotation-sidebar-toggle').click();

    document.querySelector('.bd-annotation-feed-actions button').click();
    const card = document.querySelector('.bd-annotation-feed-item[data-id="8"]');
    const inlineComposer = card.querySelector('.bd-annotation-composer');
    expect(inlineComposer).not.toBeNull();
    expect(document.querySelector('#bd-annotation-sidebar > .bd-annotation-composer')).toBeNull();
    expect(inlineComposer.hidden).toBe(false);
    expect(inlineComposer.dataset.editId).toBe('8');
    expect(inlineComposer.querySelector('.bd-annotation-composer-quote').textContent).toBe('可批');
    expect(inlineComposer.querySelector('.bd-annotation-composer-text').value).toBe('原评注');
    expect(inlineComposer.querySelector('.bd-annotation-visibility').value).toBe('PUBLIC');
    const editor = inlineComposer.querySelector('.bd-annotation-composer-text');
    editor.value = '修改后的评注';
    window.fetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ ...window.__ANNOTATIONS__[0], annotationText: '修改后的评注' }) });
    inlineComposer.querySelector('.bd-annotation-composer-save').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(window.fetch).toHaveBeenLastCalledWith(expect.stringContaining('/annotations/8'), expect.objectContaining({ method: 'PATCH' }));
    expect(document.querySelector('.bd-annotation-feed-text').textContent).toBe('修改后的评注');
  });

  test('cancelling an in-place edit restores the original annotation card', () => {
    window.__ANNOTATIONS__ = [
      { id: 8, selectedText: '可批', annotationText: '原评注', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);
    document.querySelector('#bd-annotation-sidebar-toggle').click();

    document.querySelector('.bd-annotation-feed-actions button').click();
    document.querySelector('.bd-annotation-composer-cancel').click();

    const card = document.querySelector('.bd-annotation-feed-item[data-id="8"]');
    expect(card.querySelector('.bd-annotation-composer')).toBeNull();
    expect(card.querySelector('.bd-annotation-feed-text').textContent).toBe('原评注');
    expect(card.querySelector('.bd-annotation-feed-actions button').textContent).toBe('编辑');
    expect(document.querySelector('#bd-annotation-sidebar > .bd-annotation-composer').hidden).toBe(true);
  });

  test('uses compact bookish typography and one type-colored rule above the annotation text', () => {
    expect(annotationCss).toMatch(/\.bd-annotation-feed-item > blockquote,\s*\.bd-annotation-composer-quote\s*\{[^}]*border-bottom:\s*\.5px solid var\(--bd-annotation-item-color,[^}]*}/s);
    expect(annotationCss).toMatch(/\.bd-annotation-feed-text\s*\{[^}]*font-size:\s*13px;[^}]*font-weight:\s*400;[^}]*}/s);
    expect(annotationCss).not.toMatch(/\.bd-annotation-feed-text\s*\{[^}]*border-bottom:/s);
  });

  test('clips a followed comment at the feed boundary instead of fading the whole card', async () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批', annotationText: '评论', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 }
    ];
    window.getComputedStyle = jest.fn(() => ({ position: 'fixed' }));
    window.requestAnimationFrame = callback => callback();
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><div class="bd-annotation-layout-switch" role="group"><button type="button" data-bd-layout="follow" aria-pressed="true">跟随</button><button type="button" data-bd-layout="compact" aria-pressed="false">紧凑</button></div><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;

    eval(annotationJs);
    const feed = document.querySelector('.bd-annotation-feed');
    Object.defineProperty(feed, 'clientHeight', { configurable: true, value: 400 });
    feed.getBoundingClientRect = () => ({ top: 100, bottom: 500 });
    document.querySelector('mark[data-id="1"]').getBoundingClientRect = () => ({ top: 20, bottom: 40 });

    document.querySelector('#bd-annotation-sidebar-toggle').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    const item = document.querySelector('.bd-annotation-feed-item');
    expect(item.style.transform).toBe('translate3d(0, -80px, 0)');
    expect(item.style.opacity).toBe('');
  });

  test('renders safe Markdown in a comment while preserving the source for in-place editing', () => {
    window.__ANNOTATIONS__ = [
      { id: 8, selectedText: '可批', annotationText: '**重点**', annotationHtml: '<p><strong>重点</strong></p>', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;

    eval(annotationJs);
    document.querySelector('#bd-annotation-sidebar-toggle').click();

    const card = document.querySelector('.bd-annotation-feed-item[data-id="8"]');
    expect(card.querySelector('.bd-annotation-feed-text strong').textContent).toBe('重点');
    card.querySelector('.bd-annotation-feed-actions button').click();
    expect(card.querySelector('.bd-annotation-composer-text').value).toBe('**重点**');
  });

  test('renders safe Markdown in the mobile annotation note', () => {
    window.matchMedia = jest.fn(() => ({ matches: true }));
    window.__ANNOTATIONS__ = [
      { id: 8, selectedText: '可批', annotationText: '**重点**', annotationHtml: '<p><strong>重点</strong></p>', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 }
    ];
    eval(annotationJs);

    document.querySelector('mark[data-id="8"]').click();
    expect(document.querySelector('.bd-annotation-mobile-note strong').textContent).toBe('重点');
  });

  test('mobile note reanchors while its highlight is visible and has no redundant title', () => {
    expect(annotationJs).toContain('function updateMobileCommentPosition()');
    expect(annotationJs).toContain('if (rect.bottom <= 0 || rect.top >= window.innerHeight)');
    expect(annotationCss).not.toContain('.bd-annotation-mobile-note::before');
  });

  test('mobile note hides when scrolling moves its highlight out of view', () => {
    window.matchMedia = jest.fn(() => ({ matches: true }));
    window.__ANNOTATIONS__ = [
      { id: 8, selectedText: '可批', annotationText: '移动批注', color: 'blue', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 }
    ];
    eval(annotationJs);
    const mark = document.querySelector('mark[data-id="8"]');
    mark.getBoundingClientRect = () => ({left: 12, top: 20, bottom: 40});
    mark.click();
    const note = document.querySelector('.bd-annotation-mobile-note');
    expect(note.hidden).toBe(false);
    mark.getBoundingClientRect = () => ({left: 12, top: -30, bottom: -10});
    window.dispatchEvent(new Event('scroll'));
    expect(note.hidden).toBe(true);

    mark.getBoundingClientRect = () => ({left: 12, top: 20, bottom: 40});
    window.dispatchEvent(new Event('scroll'));
    expect(note.hidden).toBe(false);

    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(note.hidden).toBe(true);
    window.dispatchEvent(new Event('scroll'));
    expect(note.hidden).toBe(true);
  });

  test('coalesces mobile annotation scroll updates into one frame and moves with a transform', () => {
    const frames = [];
    const originalRequestAnimationFrame = global.requestAnimationFrame;
    const originalCancelAnimationFrame = global.cancelAnimationFrame;
    const requestAnimationFrame = jest.fn(callback => {
      frames.push(callback);
      return frames.length;
    });
    window.requestAnimationFrame = requestAnimationFrame;
    global.requestAnimationFrame = requestAnimationFrame;
    window.cancelAnimationFrame = jest.fn();
    global.cancelAnimationFrame = window.cancelAnimationFrame;
    window.matchMedia = jest.fn(() => ({ matches: true }));
    window.__ANNOTATIONS__ = [
      { id: 8, selectedText: '可批', annotationText: '移动批注', color: 'blue', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 }
    ];
    eval(annotationJs);
    while (frames.length) {
      frames.shift()();
    }
    const mark = document.querySelector('mark[data-id="8"]');
    mark.getBoundingClientRect = () => ({left: 12, top: 20, bottom: 40});
    mark.click();
    const note = document.querySelector('.bd-annotation-mobile-note');
    frames.length = 0;
    requestAnimationFrame.mockClear();

    mark.getBoundingClientRect = () => ({left: 16, top: 60, bottom: 80});
    window.dispatchEvent(new Event('scroll'));
    window.dispatchEvent(new Event('scroll'));
    window.dispatchEvent(new Event('scroll'));

    expect(requestAnimationFrame).toHaveBeenCalledTimes(1);
    frames.shift()();
    expect(note.style.transform).toContain('translate3d(16px, 90px, 0)');
    expect(note.style.top).toBe('');
    global.requestAnimationFrame = originalRequestAnimationFrame;
    global.cancelAnimationFrame = originalCancelAnimationFrame;
    window.requestAnimationFrame = originalRequestAnimationFrame;
    window.cancelAnimationFrame = originalCancelAnimationFrame;
  });

  test('keeps Markdown blocks compact inside the annotation typography', () => {
    expect(annotationCss).toMatch(/\.bd-annotation-feed-text\s*\{[^}]*white-space:\s*normal;[^}]*}/s);
    expect(annotationCss).toContain('.bd-annotation-feed-text > :first-child { margin-top: 0; }');
    expect(annotationCss).toContain('.bd-annotation-feed-text h1,');
    expect(annotationCss).toContain('.bd-annotation-feed-text img,');
  });

  test('toolbar comment badge uses a self-contained high contrast color', () => {
    expect(annotationCss).toContain('.bd-annotation-toolbar-count');
    expect(annotationCss).toContain('background: #d83c55;');
    expect(annotationCss).toContain('color: #fff;');
    expect(annotationCss).toContain('border: 2px solid #fff;');
  });

  test('solid highlights do not create gaps where inline text fragments meet', () => {
    expect(annotationCss).toContain('padding: 0 0 2px;');
    expect(annotationCss).toContain('text-decoration-skip-ink: none;');
  });

  test('wide-screen comment rail stays fixed while cards follow the article scroll', () => {
    expect(annotationCss).toContain('right: 10vw;');
    expect(annotationCss).toContain('display: flex;');
    // 跟随型布局：卡片按划线高度绝对定位，随正文滚动联动（非静止列表）。
    expect(annotationJs).toContain('const transform = `translate3d(0, ${top}px, 0)`;');
    expect(annotationJs).toContain('const markRect = mark && mark.getBoundingClientRect();');
    expect(annotationJs).toContain('const anchorTop = markRect.top - feedRect.top + feed.scrollTop;');
  });

  test('comment feed clips exactly at the compact sidebar header divider', () => {
    expect(annotationCss).toMatch(/\.bd-annotation-sidebar\s*\{[^}]*display:\s*flex;[^}]*flex-direction:\s*column;[^}]*}/s);
    expect(annotationCss).toMatch(/\.bd-annotation-sidebar-header\s*\{[^}]*padding:\s*21px 21px 6px;[^}]*}/s);
    expect(annotationCss).toMatch(/\.bd-annotation-feed\s*\{[^}]*position:\s*relative;[^}]*flex:\s*1 1 auto;[^}]*min-height:\s*0;[^}]*overflow-y:\s*auto;[^}]*}/s);
    expect(annotationCss).not.toMatch(/\.bd-annotation-feed\s*\{[^}]*top:/s);
  });

  test('dismisses the selection menu after an outside click even if the browser retains the old selection', () => {
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3); range.getBoundingClientRect = () => ({left: 10, bottom: 20});
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));

    const popup = document.querySelector('.bd-annotation-popup');
    expect(popup.classList.contains('bd-annotation-popup-open')).toBe(true);
    document.querySelector('.content').dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    document.querySelector('.content').dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    expect(popup.classList.contains('bd-annotation-popup-open')).toBe(false);
    expect(window.getSelection().rangeCount).toBe(0);
  });

  test('keeps the selection menu visible after the trailing click in ordinary article content', () => {
    const content = document.querySelector('.content');
    const text = content.firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3); range.getBoundingClientRect = () => ({left: 10, bottom: 20});
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);

    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    content.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    expect(document.querySelector('.bd-annotation-popup').classList.contains('bd-annotation-popup-open')).toBe(true);
    expect(document.querySelector('.bd-annotation-popup [data-highlight]')).not.toBeNull();
  });

  test('does not suppress the next real selection after the dismissed click ends in the sidebar', () => {
    const text = document.querySelector('.content').firstChild;
    const firstRange = document.createRange(); firstRange.setStart(text, 0); firstRange.setEnd(text, 3); firstRange.getBoundingClientRect = () => ({left: 10, bottom: 20});
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(firstRange);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));

    document.querySelector('.content').dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    document.querySelector('#bd-annotation-sidebar').dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));

    const secondRange = document.createRange(); secondRange.setStart(text, 3); secondRange.setEnd(text, 5); secondRange.getBoundingClientRect = () => ({left: 10, bottom: 20});
    selection.removeAllRanges(); selection.addRange(secondRange);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));

    expect(document.querySelector('.bd-annotation-popup [data-highlight]')).not.toBeNull();
  });

  test('clicking an owned pure underline exposes a visible delete action', async () => {
    window.__ANNOTATIONS__ = [
      { id: 7, selectedText: '可批', annotationText: null, color: 'yellow', visibility: 'PRIVATE', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    document.querySelector('mark[data-id="7"]').click();
    const deleteAction = document.querySelector('.bd-annotation-popup [data-delete-annotation]');
    expect(deleteAction).not.toBeNull();
    expect(document.querySelector('.bd-annotation-popup').classList.contains('bd-annotation-popup-open')).toBe(true);

    deleteAction.click();
    await Promise.resolve();
    expect(window.fetch).toHaveBeenCalledWith(expect.stringContaining('/annotations/7'), expect.objectContaining({ method: 'DELETE' }));
  });

  test('renders overlapping highlights without changing the article text', () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批注文', annotationText: null, color: 'yellow', visibility: 'PRIVATE', startOffset: 0, endOffset: 4 },
      { id: 2, selectedText: '注文本', annotationText: null, color: 'red', visibility: 'PRIVATE', startOffset: 2, endOffset: 5 }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;

    eval(annotationJs);

    expect(document.querySelector('.content').textContent).toBe('可批注文本');
    expect(document.querySelector('mark[data-id="1"] mark[data-id="2"]')).not.toBeNull();
  });

  test('keeps the mark and reports failure when deleting an underline fails', async () => {
    window.__ANNOTATIONS__ = [
      { id: 7, selectedText: '可批', annotationText: null, color: 'yellow', visibility: 'PRIVATE', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    document.querySelector('mark[data-id="7"]').click();
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 500 }));
    document.querySelector('.bd-annotation-popup [data-delete-annotation]').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(window.fetch).toHaveBeenCalledWith(expect.stringContaining('/annotations/7'), expect.objectContaining({ method: 'DELETE' }));
    // 失败时保留 mark，UI 不变；popup 重新弹出并展示失败反馈。
    expect(document.querySelector('mark[data-id="7"]')).not.toBeNull();
    expect(document.querySelector('.bd-annotation-popup').classList.contains('bd-annotation-popup-open')).toBe(true);
    expect(document.querySelector('.bd-annotation-copy-result').textContent).toBe('删除失败，请重试');
  });

  test('reports failure and leaves the color menu when creating a highlight fails', async () => {
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    const content = document.querySelector('.content');
    const range = document.createRange();
    range.setStart(content.firstChild, 0);
    range.setEnd(content.firstChild, 2);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    content.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    document.querySelector('.bd-annotation-popup [data-highlight]').click();
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 500 }));
    document.querySelector('.bd-annotation-popup [data-color="yellow"]').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(window.fetch).toHaveBeenCalledWith(expect.stringContaining('/annotations'), expect.objectContaining({ method: 'POST' }));
    // 失败时不写入 mark；popup 展示失败反馈。
    expect(document.querySelector('mark[data-id="1"]')).toBeNull();
    expect(document.querySelector('.bd-annotation-copy-result').textContent).toBe('划线失败，请重试');
  });

  test('refreshes csrf token and retries after 403 when creating a highlight', async () => {
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    const content = document.querySelector('.content');
    const range = document.createRange();
    range.setStart(content.firstChild, 0);
    range.setEnd(content.firstChild, 2);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    content.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    document.querySelector('.bd-annotation-popup [data-highlight]').click();

    // 顺序：POST(403) → GET 刷新页面拿到新 token → POST 重试成功
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 403 }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: true, text: () => Promise.resolve('<meta name="_csrf" content="token2">') }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: true, json: () => Promise.resolve({ id: 1, selectedText: '可批', color: 'yellow', visibility: 'PRIVATE', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }) }));
    document.querySelector('.bd-annotation-popup [data-color="yellow"]').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    // 首次 POST 带旧 token；刷新后第二次 POST 带新 token。
    expect(window.fetch.mock.calls[0][1].headers['X-CSRF-TOKEN']).toBe('token');
    expect(window.fetch.mock.calls[2][1].headers['X-CSRF-TOKEN']).toBe('token2');
    expect(window.fetch.mock.calls[2][1].method).toBe('POST');
    // 重试成功后写入 mark，不显示失败。
    expect(document.querySelector('mark[data-id="1"]')).not.toBeNull();
    expect(document.querySelector('.bd-annotation-copy-result')).toBeNull();
  });

  test('retries after 403 when saving a comment', async () => {
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    const content = document.querySelector('.content');
    const range = document.createRange();
    range.setStart(content.firstChild, 0);
    range.setEnd(content.firstChild, 2);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    document.querySelector('.bd-annotation-popup [data-comment]').click();
    const input = document.querySelector('.bd-annotation-composer-text');
    input.value = '我的评论';

    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 403 }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: true, text: () => Promise.resolve('<meta name="_csrf" content="token2">') }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: true, json: () => Promise.resolve({ id: 2, selectedText: '可批', annotationText: '我的评论', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }) }));
    document.querySelector('.bd-annotation-composer-save').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(window.fetch.mock.calls[2][1].headers['X-CSRF-TOKEN']).toBe('token2');
    // 重试成功：composer 关闭，且未出现失败文案。
    expect(document.querySelector('.bd-annotation-composer').hidden).toBe(true);
    expect(document.querySelector('.bd-annotation-composer-save').textContent).not.toBe('保存失败，请重试');
  });

  test('reports data-status 403 when token refresh cannot find a new meta', async () => {
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    const content = document.querySelector('.content');
    const range = document.createRange();
    range.setStart(content.firstChild, 0);
    range.setEnd(content.firstChild, 2);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    content.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    document.querySelector('.bd-annotation-popup [data-highlight]').click();

    // POST(403) → 刷新页面无 meta → 重试仍 403 → 失败且带状态码
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 403 }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: true, text: () => Promise.resolve('<html><body>no meta here</body></html>') }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 403 }));
    document.querySelector('.bd-annotation-popup [data-color="yellow"]').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(document.querySelector('.bd-annotation-copy-result').textContent).toBe('划线失败，请重试');
    expect(document.querySelector('.bd-annotation-copy-result').getAttribute('data-status')).toBe('403');
  });

  test('reports data-status 500 for non-403 failures without retrying', async () => {
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    const content = document.querySelector('.content');
    const range = document.createRange();
    range.setStart(content.firstChild, 0);
    range.setEnd(content.firstChild, 2);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    content.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    document.querySelector('.bd-annotation-popup [data-highlight]').click();

    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 500 }));
    document.querySelector('.bd-annotation-popup [data-color="yellow"]').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    // 非 403 不触发刷新重试，只发一次请求。
    expect(window.fetch).toHaveBeenCalledTimes(1);
    expect(document.querySelector('.bd-annotation-copy-result').getAttribute('data-status')).toBe('500');
  });

  test('reports data-status 0 for network errors', async () => {
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    const content = document.querySelector('.content');
    const range = document.createRange();
    range.setStart(content.firstChild, 0);
    range.setEnd(content.firstChild, 2);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    content.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    document.querySelector('.bd-annotation-popup [data-highlight]').click();

    window.fetch.mockReturnValueOnce(Promise.reject(new Error('network down')));
    document.querySelector('.bd-annotation-popup [data-color="yellow"]').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(document.querySelector('.bd-annotation-copy-result').getAttribute('data-status')).toBe('0');
  });

  test('keeps the old token when the refresh page itself returns non-ok', async () => {
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    const content = document.querySelector('.content');
    const range = document.createRange();
    range.setStart(content.firstChild, 0);
    range.setEnd(content.firstChild, 2);
    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    content.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    document.querySelector('.bd-annotation-popup [data-highlight]').click();

    // POST(403) → 刷新页面自身返回 500 → 保留旧 token → 重试仍 403 → 失败带状态码
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 403 }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 500 }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 403 }));
    document.querySelector('.bd-annotation-popup [data-color="yellow"]').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(document.querySelector('.bd-annotation-copy-result').getAttribute('data-status')).toBe('403');
  });

  test('refreshes csrf token and retries a 403 delete with a 204 response', async () => {
    window.__ANNOTATIONS__ = [
      { id: 7, selectedText: '可批', annotationText: null, color: 'yellow', visibility: 'PRIVATE', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-type-picker"><button type="button" class="bd-annotation-type-trigger"><span class="bd-annotation-type-label"></span></button><div class="bd-annotation-type-menu" hidden><button data-bd-annotation-type="blue"></button><button data-bd-annotation-type="yellow"></button><button data-bd-annotation-type="green"></button><button data-bd-annotation-type="red"></button></div></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);

    document.querySelector('mark[data-id="7"]').click();
    // DELETE(403) → GET 刷新拿新 token → DELETE 重试 204 成功
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 403 }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: true, text: () => Promise.resolve('<meta name="_csrf" content="token2">') }));
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: true, status: 204 }));
    document.querySelector('.bd-annotation-popup [data-delete-annotation]').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    // 重试 DELETE 用新 token，且 204 不解析 body；mark 被移除。
    expect(window.fetch.mock.calls[2][1].headers['X-CSRF-TOKEN']).toBe('token2');
    expect(window.fetch.mock.calls[2][1].method).toBe('DELETE');
    expect(document.querySelector('mark[data-id="7"]')).toBeNull();
  });

  test('styles remain inside the bd-annotation namespace', () => {
    expect(annotationCss).not.toMatch(/(^|\n)\.content\s+/);
    expect(annotationCss).toContain('.bd-annotation-sidebar');
    expect(annotationCss).toContain('.bd-annotation-popup');
    expect(annotationCss).toContain('button[data-color]');
    expect(annotationCss).toContain('background: transparent');
    expect(annotationCss).toContain('color: inherit');
    expect(annotationCss).toContain('text-decoration-style: wavy');
    expect(annotationCss).toContain('text-decoration-style: double');
    expect(annotationCss).toContain('.bd-annotation-highlight.bd-annotation-has-comment');
    expect(annotationCss).not.toContain('.bd-annotation-feed-item::before');
    expect(annotationCss).toContain('border-left: 2px solid var(--bd-annotation-item-color, #9ab2ff);');
    expect(annotationCss).toContain('border-style: dashed');
    expect(annotationCss).toContain('.bd-annotation-comment-trigger');
    expect(annotationCss).toContain('transform: translateY(-50%);');
    expect(annotationJs).toContain('const labelGutter = index === 0 ? 9 : 0;');
    expect(annotationJs).toContain('item.hidden = false;');
    // 跟随型定位与 compact 重置两种分支并存。
    expect(annotationJs).toContain("item.style.transform = '';");
    expect(annotationJs).toContain("item.style.opacity = '';");
    expect(annotationJs).toContain('const transform = `translate3d(0, ${top}px, 0)`;');
    expect(annotationJs).toContain('item.style.opacity = \'\';');
    expect(annotationCss).toContain('bd-annotation-pop-in 150ms ease-out forwards');
    // 只分两档：手机屏(≤768px) 与 PC屏(≥769px)，不再细分中屏/大屏。
    expect(annotationCss).not.toContain('max-width: 1599px');
    expect(annotationCss).not.toContain('@media (min-width: 1600px)');
    expect(annotationCss).toContain('@media (min-width: 769px)');
    expect(annotationCss).toContain('position: fixed');
    // 聚焦批注选中态：只加深卡片边框，不改背景/文字。
    expect(annotationCss).toContain('.bd-annotation-feed-item-active');
    expect(annotationCss).toContain('.bd-annotation-feed-item-active::before');
    expect(annotationCss).toContain('width: 4px');
    expect(annotationCss).toContain('border-color: #5b6b85');
    expect(annotationCss).toContain('.bd-annotation-comment-outline-active');
    // PC(≥769px) 统一并排：正文 1fr 填满 80vw container，侧栏相对宽度浮右侧。
    expect(annotationCss).toContain('grid-template-columns: minmax(0, 1fr) clamp(240px, 24vw, 360px)');
    expect(annotationCss).toContain('right: 10vw;');
    // 工具栏位置不随侧栏开关改变（保持原始 right: calc(10vw - 68px)，不覆盖）。
    expect(annotationCss).not.toContain('right: calc(50% - 760px);');
    // 侧栏顶部与文章卡片齐平（导航栏 60 + margin 32 = 92），底部留 36px。
    expect(annotationCss).toContain('top: 92px;');
    expect(annotationCss).toContain('height: calc(100dvh - 128px);');
    expect(annotationCss).not.toMatch(/\.bd-annotation-comments-open \.reading-toolbar\s*\{\s*display: none;/);
    // 侧栏关闭时原地淡出（仅 opacity 过渡），不平移飞出浏览器。
    expect(annotationCss).not.toContain('translateX(calc(100% + 32px))');
    expect(annotationCss).not.toContain('transform: translateX(100%);');
    // 点击卡片/正文标签聚焦批注：focusAnnotation 统一处理选中与滚动定位，再次聚焦同一条则取消。
    expect(annotationJs).toContain('function focusAnnotation(annotationId)');
    expect(annotationJs).toContain('const same = activeAnnotationId === annotationId');
    expect(annotationJs).toContain('activeAnnotationId = same ? null : annotationId');
    expect(annotationJs).toContain('function updateActiveItem()');
    // #2 写操作失败反馈（popup 删除/划线 + 侧栏删除按钮）。
    expect(annotationJs).toContain('删除失败，请重试');
    expect(annotationJs).toContain('划线失败，请重试');
  });
});
