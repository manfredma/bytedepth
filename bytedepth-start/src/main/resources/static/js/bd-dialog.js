(function () {
    'use strict';

    function createButton(className, label, type) {
        const button = document.createElement('button');
        button.className = className;
        button.type = type;
        button.textContent = label;
        return button;
    }

    function createDialog() {
        const dialog = document.createElement('dialog');
        dialog.className = 'bd-dialog';
        dialog.setAttribute('aria-labelledby', 'bd-dialog-title');
        dialog.setAttribute('aria-describedby', 'bd-dialog-message');

        const form = document.createElement('form');
        form.className = 'bd-dialog__surface';
        form.method = 'dialog';

        const title = document.createElement('h2');
        title.className = 'bd-dialog__title';
        title.id = 'bd-dialog-title';

        const message = document.createElement('p');
        message.className = 'bd-dialog__message';
        message.id = 'bd-dialog-message';

        const actions = document.createElement('div');
        actions.className = 'bd-dialog__actions';
        const cancel = createButton('bd-dialog__button bd-dialog__button--cancel', '取消', 'submit');
        const confirm = createButton('bd-dialog__button bd-dialog__button--confirm', '确认', 'button');
        actions.append(cancel, confirm);
        form.append(title, message, actions);
        dialog.append(form);
        document.body.append(dialog);

        return { dialog: dialog, title: title, message: message, confirm: confirm };
    }

    let elements;
    const acceptedForms = new WeakSet();

    function confirm(options) {
        elements = elements || createDialog();
        const dialog = elements.dialog;
        const activeElement = document.activeElement;
        elements.title.textContent = options.title || '请确认操作';
        elements.message.textContent = options.message || '';
        elements.confirm.textContent = options.confirmText || '确认';
        dialog.dataset.tone = options.tone || 'default';

        return new Promise(function (resolve) {
            function closeHandler() {
                dialog.removeEventListener('close', closeHandler);
                if (activeElement && activeElement.isConnected) {activeElement.focus();}
                resolve(dialog.returnValue === 'confirm');
            }

            elements.confirm.onclick = function () { dialog.close('confirm'); };
            dialog.addEventListener('close', closeHandler);
            dialog.showModal();
            elements.confirm.focus();
        });
    }

    function formOptions(form) {
        return {
            title: form.dataset.bdConfirmTitle,
            message: form.dataset.bdConfirmMessage,
            tone: form.dataset.bdConfirmTone,
            confirmText: form.dataset.bdConfirmConfirmText
        };
    }

    document.addEventListener('submit', function (event) {
        const form = event.target.closest('form[data-bd-confirm]');
        if (!form) {return;}
        if (acceptedForms.has(form)) {
            acceptedForms.delete(form);
            return;
        }

        event.preventDefault();
        const submitter = event.submitter;
        confirm(formOptions(form)).then(function (accepted) {
            if (!accepted) {return;}
            acceptedForms.add(form);
            if (form.requestSubmit && submitter) {form.requestSubmit(submitter);}
            else if (form.requestSubmit) {form.requestSubmit();}
            else {form.submit();}
            acceptedForms.delete(form);
        });
    });

    window.BytedepthDialog = { confirm: confirm };
})();
