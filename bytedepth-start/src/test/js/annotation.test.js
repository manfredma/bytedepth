/**
 * @jest-environment jsdom
 */
const fs = require('fs');
const path = require('path');

const annotationJs = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/annotation.js'),
  'utf-8'
);
const annotationCss = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/css/annotation.css'),
  'utf-8'
);

describe('annotation.js popup positioning', () => {
  let offsetWidthDescriptor;
  let offsetHeightDescriptor;

  beforeEach(() => {
    document.body.innerHTML = '<article id="post-article"><div class="content">可批注文本</div></article>';
    window.__ANNOTATIONS__ = [];
    window.__CURRENT_USER_ID__ = 1;
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 400 });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 300 });
    offsetWidthDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth');
    offsetHeightDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetHeight');
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', {
      configurable: true,
      get() { return this.classList.contains('annotation-popup') && this.classList.contains('open') ? 340 : 0; }
    });
    Object.defineProperty(HTMLElement.prototype, 'offsetHeight', {
      configurable: true,
      get() { return this.classList.contains('annotation-popup') && this.classList.contains('open') ? 180 : 0; }
    });
  });

  afterEach(() => {
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', offsetWidthDescriptor);
    Object.defineProperty(HTMLElement.prototype, 'offsetHeight', offsetHeightDescriptor);
    delete window.__ANNOTATIONS__;
    delete window.__CURRENT_USER_ID__;
  });

  test('positions an opened popup within the viewport when the selection is near its bottom', () => {
    eval(annotationJs);
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange();
    range.setStart(text, 0);
    range.setEnd(text, 4);
    range.getBoundingClientRect = () => ({ left: 100, width: 40, top: 310, bottom: 330 });

    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));

    const popup = document.querySelector('.annotation-popup');
    expect(popup.classList.contains('open')).toBe(true);
    expect(popup.style.left).toBe('8px');
    expect(popup.style.top).toBe('112px');
  });

  test('places a popup below a selection near the viewport top without crossing the top edge', () => {
    eval(annotationJs);
    const text = document.querySelector('.content').firstChild;
    const range = document.createRange();
    range.setStart(text, 0);
    range.setEnd(text, 4);
    range.getBoundingClientRect = () => ({ left: 100, width: 40, top: 4, bottom: 24 });

    const selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));

    const popup = document.querySelector('.annotation-popup');
    expect(popup.style.top).toBe('32px');
  });

  test('uses viewport-relative popup positioning and keeps its width within narrow viewports', () => {
    expect(annotationCss).toMatch(/\.annotation-popup\s*\{[^}]*position:\s*fixed;/s);
    expect(annotationCss).toMatch(/\.annotation-popup\s*\{[^}]*max-width:\s*calc\(100vw - 16px\);/s);
    expect(annotationCss).toMatch(/\.annotation-popup\s*\{[^}]*box-sizing:\s*border-box;/s);
  });
});
