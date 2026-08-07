/**
 * @jest-environment jsdom
 */
const fs = require('fs');
const path = require('path');

const themeSwitcherJs = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/theme-switcher.js'),
  'utf-8'
);

describe('theme-switcher.js', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-theme');
    localStorage.clear();
    document.body.innerHTML = `
      <div class="theme-switcher">
        <button class="theme-trigger" aria-expanded="false">主题</button>
        <div class="theme-options">
          <button data-theme-option="default">默认</button>
          <button data-theme-option="paper">纸张</button>
          <button data-theme-option="midnight">深夜</button>
        </div>
      </div>
    `;
  });

  test('applies default theme when no theme is stored', () => {
    eval(themeSwitcherJs);
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
  });

  test('persists and restores selected theme via localStorage', () => {
    localStorage.setItem('bytedepth.theme', 'midnight');
    eval(themeSwitcherJs);
    expect(document.documentElement.getAttribute('data-theme')).toBe('midnight');
  });

  test('falls back to default when stored theme is invalid', () => {
    localStorage.setItem('bytedepth.theme', 'invalid');
    eval(themeSwitcherJs);
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
  });

  test('toggles menu open/close on trigger click', () => {
    eval(themeSwitcherJs);
    const trigger = document.querySelector('.theme-trigger');
    const switcher = document.querySelector('.theme-switcher');

    trigger.click();
    expect(switcher.classList.contains('open')).toBe(true);
    expect(trigger.getAttribute('aria-expanded')).toBe('true');

    trigger.click();
    expect(switcher.classList.contains('open')).toBe(false);
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });

  test('selecting a theme closes the menu and updates aria attributes', () => {
    eval(themeSwitcherJs);
    const trigger = document.querySelector('.theme-trigger');
    const option = document.querySelector('[data-theme-option="paper"]');

    trigger.click();
    option.click();

    expect(document.querySelector('.theme-switcher').classList.contains('open')).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('paper');
    expect(localStorage.getItem('bytedepth.theme')).toBe('paper');
  });

  test('clicking outside closes all open menus', () => {
    eval(themeSwitcherJs);
    const trigger = document.querySelector('.theme-trigger');
    trigger.click();
    expect(document.querySelector('.theme-switcher').classList.contains('open')).toBe(true);

    document.dispatchEvent(new Event('click'));
    expect(document.querySelector('.theme-switcher').classList.contains('open')).toBe(false);
  });
});