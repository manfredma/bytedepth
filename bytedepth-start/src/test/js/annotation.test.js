/** @jest-environment jsdom */
const fs = require('fs');
const path = require('path');
const annotationJs = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/annotation.js'), 'utf-8');
const annotationCss = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/annotation.css'), 'utf-8');

describe('annotation sidebar', () => {
  beforeEach(() => {
    localStorage.clear();
    document.body.innerHTML = `<meta name="_csrf" content="token"><article id="post-article" class="bd-annotation-scope"><button id="bd-annotation-sidebar-toggle"></button><div class="content">可批注文本</div><aside id="bd-annotation-sidebar"><button class="bd-annotation-sidebar-close"></button><section class="bd-annotation-composer" hidden><div class="bd-annotation-composer-quote"></div><div class="bd-annotation-color-row"><button data-bd-annotation-color="yellow"></button></div><textarea class="bd-annotation-composer-text"></textarea><select class="bd-annotation-visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">私有</option></select><button class="bd-annotation-composer-cancel"></button><button class="bd-annotation-composer-save"></button></section><section class="bd-annotation-feed"></section></aside></article>`;
    window.__ANNOTATIONS__ = [];
    window.fetch = jest.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({ id: 1, selectedText: '可批注', color: 'yellow', visibility: 'PRIVATE', startOffset: 0, endOffset: 3, ownedByCurrentVisitor: true }) }));
    eval(annotationJs);
  });
  afterEach(() => { delete window.__ANNOTATIONS__; delete window.fetch; });

  test('toggle persists sidebar state', () => {
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    expect(document.querySelector('#bd-annotation-sidebar').classList.contains('bd-annotation-sidebar-open')).toBe(true);
    expect(localStorage.getItem('bd.annotation.sidebar.open')).toBe('true');
    document.querySelector('.bd-annotation-sidebar-close').click();
    expect(localStorage.getItem('bd.annotation.sidebar.open')).toBe('false');
  });

  test('typing a comment defaults visibility to public while a blank highlight stays private', () => {
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3);
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    document.querySelector('#bd-annotation-sidebar-toggle').click();
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    const visibility = document.querySelector('.bd-annotation-visibility');
    expect(visibility.value).toBe('PRIVATE');
    const input = document.querySelector('.bd-annotation-composer-text'); input.value = '我的评论'; input.dispatchEvent(new Event('input', { bubbles: true }));
    expect(visibility.value).toBe('PUBLIC');
  });

  test('closed sidebar uses compact menu and posts a private pure highlight', async () => {
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange(); range.setStart(text, 0); range.setEnd(text, 3); range.getBoundingClientRect = () => ({left: 10, bottom: 20});
    const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    document.querySelector('.bd-annotation-popup [data-color]').click();
    await Promise.resolve();
    expect(window.fetch).toHaveBeenCalledWith(expect.stringContaining('/annotations'), expect.objectContaining({ method: 'POST' }));
    expect(JSON.parse(window.fetch.mock.calls[0][1].body)).toMatchObject({ annotationText: null, visibility: 'PRIVATE' });
  });

  test('styles remain inside the bd-annotation namespace', () => {
    expect(annotationCss).not.toMatch(/(^|\n)\.content\s+/);
    expect(annotationCss).toContain('.bd-annotation-sidebar');
    expect(annotationCss).toContain('.bd-annotation-popup');
  });
});
