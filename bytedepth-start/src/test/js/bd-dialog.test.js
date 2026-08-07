/**
 * @jest-environment jsdom
 */
const fs = require('fs');
const path = require('path');

const dialogJs = fs.readFileSync(
  path.resolve(__dirname, '../../main/resources/static/js/bd-dialog.js'),
  'utf-8'
);

describe('bd-dialog.js', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    delete window.BytedepthDialog;
  });

  test('exposes BytedepthDialog.confirm after loading', () => {
    eval(dialogJs);
    expect(window.BytedepthDialog).toBeDefined();
    expect(typeof window.BytedepthDialog.confirm).toBe('function');
  });

  test('confirm creates a dialog element in the DOM', () => {
    eval(dialogJs);
    window.BytedepthDialog.confirm({ title: '测试', message: '确认?', confirmText: '确定' });
    const dialog = document.querySelector('.bd-dialog');
    expect(dialog).not.toBeNull();
    expect(dialog.querySelector('.bd-dialog__title').textContent).toBe('测试');
    expect(dialog.querySelector('.bd-dialog__message').textContent).toBe('确认?');
    expect(dialog.querySelector('.bd-dialog__button--confirm').textContent).toBe('确定');
  });

  test('confirm returns a Promise', () => {
    eval(dialogJs);
    const result = window.BytedepthDialog.confirm({ title: '测试' });
    expect(result).toBeInstanceOf(Promise);
  });

  test('confirm with confirmText option sets button label', () => {
    eval(dialogJs);
    window.BytedepthDialog.confirm({ title: '删除', message: '确定删除?', confirmText: '删除' });
    const confirmBtn = document.querySelector('.bd-dialog__button--confirm');
    expect(confirmBtn.textContent).toBe('删除');
  });

  test('confirm with tone option sets data attribute', () => {
    eval(dialogJs);
    window.BytedepthDialog.confirm({ title: '删除', message: '确定删除?', tone: 'danger' });
    const dialog = document.querySelector('.bd-dialog');
    expect(dialog.dataset.tone).toBe('danger');
  });

  test('confirm with default options', () => {
    eval(dialogJs);
    window.BytedepthDialog.confirm({});
    const dialog = document.querySelector('.bd-dialog');
    expect(dialog.querySelector('.bd-dialog__title').textContent).toBe('请确认操作');
    expect(dialog.querySelector('.bd-dialog__button--confirm').textContent).toBe('确认');
  });

  });