(function () {
    'use strict';

    function csrfHeaders() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        if (!token || !header || !token.content || !header.content) return {};
        var headers = {};
        headers[header.content] = token.content;
        return headers;
    }

    function toVditorUploadResponse(files, responseText) {
        var response = JSON.parse(responseText);
        var filename = files[0] && files[0].name ? files[0].name : response.filename;
        if (!response.url) throw new Error('上传服务未返回图片地址');
        return JSON.stringify({
            code: 0,
            msg: '',
            data: { errFiles: [], succMap: { [filename]: response.url } }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        var source = document.getElementById('content-editor');
        var host = document.getElementById('vditor-editor');
        var form = document.querySelector('.post-editor-form');
        if (!source || !host || !form) return;
        if (typeof Vditor === 'undefined') {
            source.hidden = false;
            source.classList.add('post-editor-fallback');
            return;
        }

        var editor = new Vditor(host, {
            value: source.value,
            mode: 'ir',
            height: 560,
            minHeight: 440,
            cache: { enable: false },
            preview: { markdown: { toc: true, mark: true } },
            upload: {
                url: '/admin/images/upload',
                fieldName: 'file',
                headers: csrfHeaders(),
                accept: 'image/*',
                multiple: false,
                format: toVditorUploadResponse
            },
            input: function (value) { source.value = value; }
        });

        form.addEventListener('submit', function () {
            source.value = editor.getValue();
        });
    });
}());
