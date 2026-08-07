// jsdom 不原生支持 <dialog> 的 showModal/close
HTMLDialogElement.prototype.showModal = function () {
  this.open = true;
};
HTMLDialogElement.prototype.close = function (returnValue) {
  this.open = false;
  this.returnValue = returnValue || '';
};