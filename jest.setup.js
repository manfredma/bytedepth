import { vi } from 'vitest';

// 保留现有测试的 Jest mock API；Vitest 的 vi 与这些调用语义一致。
globalThis.jest = vi;

// happy-dom 不原生支持 <dialog> 的 showModal/close
HTMLDialogElement.prototype.showModal = function () {
  this.open = true;
};
HTMLDialogElement.prototype.close = function (returnValue) {
  this.open = false;
  this.returnValue = returnValue || '';
};
