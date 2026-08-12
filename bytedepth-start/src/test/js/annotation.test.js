/** @jest-environment jsdom */
const fs = require('fs');
const path = require('path');
const annotationJs = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/annotation.js'), 'utf-8');
const annotationCss = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/annotation.css'), 'utf-8');

describe('annotation sidebar', () => {
  beforeEach(() => {
    localStorage.clear();
    window.matchMedia = jest.fn(() => ({ matches: false }));
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
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

  test('toggle persists sidebar state', () => {
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(localStorage.getItem('bd.annotation.sidebar.open')).toBe('true');
    document.querySelector('.bd-annotation-sidebar-close').click();
    expect(localStorage.getItem('bd.annotation.sidebar.open')).toBe('false');
  });

  test('toggle remains usable when browser storage is unavailable', () => {
    const originalGet = Storage.prototype.getItem;
    const originalSet = Storage.prototype.setItem;
    Storage.prototype.getItem = jest.fn(() => { throw new DOMException('blocked', 'SecurityError'); });
    Storage.prototype.setItem = jest.fn(() => { throw new DOMException('blocked', 'SecurityError'); });
    document.body.innerHTML = `<article id="post-article"><button id="bd-annotation-sidebar-toggle"></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section><section class="bd-annotation-feed"></section></aside></article>`;
    expect(() => eval(annotationJs)).not.toThrow();
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
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
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;

    eval(annotationJs);

    expect(document.querySelector('.bd-annotation-comment-count').textContent).toBe('2');
    expect(document.querySelector('.bd-annotation-toolbar-count').textContent).toBe('2');
    expect(document.querySelector('.bd-annotation-toolbar-count').hidden).toBe(false);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelectorAll('.bd-annotation-feed-item')).toHaveLength(2);
    expect(document.querySelector('mark[data-id="1"]').classList.contains('bd-annotation-has-comment')).toBe(true);
    expect(document.querySelector('mark[data-id="2"]').classList.contains('bd-annotation-has-comment')).toBe(false);
    expect(document.querySelector('mark[data-id="1"] .bd-annotation-comment-trigger').textContent).toBe('评注');
    expect(document.querySelectorAll('.bd-annotation-comment-trigger')).toHaveLength(1);

    document.querySelector('.bd-annotation-sidebar-close').click();
    const commentTrigger = document.querySelector('mark[data-id="1"] .bd-annotation-comment-trigger');
    commentTrigger.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    commentTrigger.click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(document.querySelector('.bd-annotation-popup')).toBeNull();
    commentTrigger.click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(false);

    window.getSelection().removeAllRanges();
    document.querySelector('mark[data-id="2"]').click();
    expect(document.querySelector('.bd-annotation-popup [data-delete-annotation]')).not.toBeNull();
    document.querySelector('.bd-annotation-popup [data-delete-annotation]').click();
    await Promise.resolve();
    expect(window.fetch).toHaveBeenCalledWith(expect.stringContaining('/annotations/2'), expect.objectContaining({ method: 'DELETE' }));

    document.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(document.querySelector('.bd-annotation-popup').classList.contains('bd-annotation-popup-open')).toBe(false);
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
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
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
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;

    eval(annotationJs);

    expect(document.querySelector('.content').textContent).toBe('可批注文本');
    expect(document.querySelector('mark[data-id="1"] mark[data-id="2"]')).not.toBeNull();
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
    expect(annotationCss).toContain('border-style: dashed');
    expect(annotationCss).toContain('.bd-annotation-comment-trigger');
    expect(annotationCss).toContain('bd-annotation-pop-in 150ms ease-out forwards');
  });
});
