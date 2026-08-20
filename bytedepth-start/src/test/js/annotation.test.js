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

  test('sidebar is closed on first visit and persists an explicit open choice', () => {
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(false);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(document.querySelector('#post-article').classList.contains('bd-annotation-reading-layout-open')).toBe(true);
    expect(localStorage.getItem('bd.annotation.sidebar.open')).toBe('true');
    document.querySelector('.bd-annotation-sidebar-close').click();
    expect(document.querySelector('#post-article').classList.contains('bd-annotation-reading-layout-open')).toBe(false);
    expect(localStorage.getItem('bd.annotation.sidebar.open')).toBe('false');
  });

  test('toggle remains usable when browser storage is unavailable', () => {
    const originalGet = Storage.prototype.getItem;
    const originalSet = Storage.prototype.setItem;
    Storage.prototype.getItem = jest.fn(() => { throw new DOMException('blocked', 'SecurityError'); });
    Storage.prototype.setItem = jest.fn(() => { throw new DOMException('blocked', 'SecurityError'); });
    document.body.innerHTML = `<article id="post-article"><button id="bd-annotation-sidebar-toggle"></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section><section class="bd-annotation-feed"></section></aside></article>`;
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
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;

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

  test('clicking the same annotation trigger twice closes the sidebar but a different one keeps it open', async () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批', annotationText: '第一条评论', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 },
      { id: 3, selectedText: '注文', annotationText: '第二条评论', color: 'red', visibility: 'PUBLIC', startOffset: 2, endOffset: 4 }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
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

    // 再次点击同一条：回收侧栏，高亮清零。
    triggerB.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    triggerB.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(false);
    expect(document.querySelectorAll('.bd-annotation-feed-item-active')).toHaveLength(0);
  });

  test('keeps feed items visible in non-sticky mid-width layout', async () => {
    window.__ANNOTATIONS__ = [
      { id: 1, selectedText: '可批', annotationText: '评论', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2 }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
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

  test('reports failure on the delete button inside the sidebar card', async () => {
    window.__ANNOTATIONS__ = [
      { id: 8, selectedText: '可批', annotationText: '评注', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);
    document.querySelector('#bd-annotation-sidebar-toggle').click();

    const deleteButton = document.querySelector('.bd-annotation-feed-actions button:last-child');
    expect(deleteButton.textContent).toBe('删除');
    window.fetch.mockReturnValueOnce(Promise.resolve({ ok: false, status: 500 }));
    deleteButton.click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(window.fetch).toHaveBeenCalledWith(expect.stringContaining('/annotations/8'), expect.objectContaining({ method: 'DELETE' }));
    // 失败时卡片保留，按钮文案变为失败提示。
    expect(document.querySelector('.bd-annotation-feed-item[data-id="8"]')).not.toBeNull();
    expect(deleteButton.textContent).toBe('删除失败，请重试');
  });

  test('edits an owned comment inside its sidebar card', async () => {
    window.__ANNOTATIONS__ = [
      { id: 8, selectedText: '可批', annotationText: '原评注', color: 'yellow', visibility: 'PUBLIC', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
    eval(annotationJs);
    document.querySelector('#bd-annotation-sidebar-toggle').click();

    document.querySelector('.bd-annotation-feed-actions button').click();
    expect(document.querySelector('.bd-annotation-inline-editor-text')).not.toBeNull();
    expect(document.querySelector('.bd-annotation-composer').hidden).toBe(true);
    const editor = document.querySelector('.bd-annotation-inline-editor-text');
    editor.value = '修改后的评注';
    window.fetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ ...window.__ANNOTATIONS__[0], annotationText: '修改后的评注' }) });
    document.querySelector('.bd-annotation-inline-editor-save').click();
    await new Promise(resolve => setTimeout(resolve, 0));

    expect(window.fetch).toHaveBeenLastCalledWith(expect.stringContaining('/annotations/8'), expect.objectContaining({ method: 'PATCH' }));
    expect(document.querySelector('.bd-annotation-feed-text').textContent).toBe('修改后的评注');
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

  test('keeps the wide-screen comment rail and its cards stationary while reading', () => {
    expect(annotationCss).toContain('left: calc(50% + 312px);');
    expect(annotationCss).toContain('display: flex;');
    expect(annotationCss).toContain('position: relative;');
    expect(annotationJs).not.toContain('item.style.top = `${top}px`;');
    expect(annotationJs).not.toContain('const markRect = mark && mark.getBoundingClientRect();');
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

  test('keeps the mark and reports failure when deleting an underline fails', async () => {
    window.__ANNOTATIONS__ = [
      { id: 7, selectedText: '可批', annotationText: null, color: 'yellow', visibility: 'PRIVATE', startOffset: 0, endOffset: 2, ownedByCurrentVisitor: true }
    ];
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
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
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"><span class="bd-annotation-toolbar-count" hidden></span></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><span class="bd-annotation-comment-count"></span><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-feed"></section><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section></aside></article>`;
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
    expect(annotationJs).toContain("item.style.top = '';");
    expect(annotationJs).toContain("item.style.opacity = '';");
    expect(annotationJs).not.toContain('let nextTop = Number.NEGATIVE_INFINITY;');
    expect(annotationJs).not.toContain('const viewportTop = feedRect.top;');
    expect(annotationJs).not.toContain('markRect.bottom > viewportTop');
    expect(annotationCss).toContain('bd-annotation-pop-in 150ms ease-out forwards');
    expect(annotationCss).toContain('max-width: 1599px');
    expect(annotationCss).toContain('position: fixed');
    expect(annotationCss).toContain('@media (min-width: 1600px)');
    // #9 聚焦批注高亮：彩色左条 + 轻染背景。
    expect(annotationCss).toContain('.bd-annotation-feed-item-active');
    expect(annotationCss).toContain('.bd-annotation-feed-item-active::before');
    expect(annotationCss).toContain('width: 4px');
    expect(annotationCss).toContain('background: var(--bd-annotation-item-color, #315efb)');
    // 宽屏批注打开时保留受限阅读宽度与完整内边距。
    expect(annotationCss).toContain('grid-template-columns: minmax(0, 980px) 360px');
    expect(annotationCss).toContain('padding: 48px 56px');
    // 批注打开时，工具栏位于右侧批注栏的外侧，而非文章与侧栏之间。
    expect(annotationCss).toContain('right: calc(50% - 760px);');
    expect(annotationCss).toContain('height: calc(100dvh - 96px);');
    expect(annotationCss).not.toMatch(/\.bd-annotation-comments-open \.reading-toolbar\s*\{\s*display: none;/);
    // #9 同一批注二次点击回收、不同批注保持打开。
    expect(annotationJs).toContain('const sameAnnotation = isOpen() && activeAnnotationId === annotation.id');
    expect(annotationJs).toContain('activeAnnotationId = sameAnnotation ? null : annotation.id');
    expect(annotationJs).toContain('function updateActiveItem()');
    // #2 写操作失败反馈（popup 删除/划线 + 侧栏删除按钮）。
    expect(annotationJs).toContain('删除失败，请重试');
    expect(annotationJs).toContain('划线失败，请重试');
  });
});
