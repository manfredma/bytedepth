/* 文章划线评论：侧栏可选，匿名归属由 HttpOnly Cookie 在服务端维护。 */
(function () {
    'use strict';
    var article = document.getElementById('post-article');
    var content = article && article.querySelector('.content');
    var sidebar = document.getElementById('bd-annotation-sidebar');
    var toggle = document.getElementById('bd-annotation-sidebar-toggle');
    if (!content || !sidebar || !toggle) return;

    var annotations = window.__ANNOTATIONS__ || [];
    var csrf = (document.querySelector('meta[name="_csrf"]') || {}).content || '';
    var composer = sidebar.querySelector('.bd-annotation-composer');
    var feed = sidebar.querySelector('.bd-annotation-feed');
    var selected = null, color = 'yellow', visibilityChanged = false, popup, tooltip;
    var storageKey = 'bd.annotation.sidebar.open';

    // 浏览器可能禁用本地存储（隐私模式、嵌入式 WebView 等）；不能因此让整套交互失效。
    function readSidebarState() {
        try { return localStorage.getItem(storageKey) === 'true'; } catch (ignored) { return false; }
    }
    function persistSidebarState(open) {
        try { localStorage.setItem(storageKey, String(open)); } catch (ignored) { /* 仅不记忆状态 */ }
    }

    function api(path, method, payload) {
        return fetch(window.location.pathname + '/annotations' + path, {
            method: method, headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf},
            body: payload ? JSON.stringify(payload) : undefined
        }).then(function (response) { if (!response.ok) throw new Error('annotation request failed'); return response.status === 204 ? null : response.json(); });
    }
    function isOpen() { return sidebar.classList.contains('bd-annotation-sidebar-open'); }
    function setOpen(open) {
        sidebar.classList.toggle('bd-annotation-sidebar-open', open);
        sidebar.setAttribute('aria-hidden', String(!open)); toggle.setAttribute('aria-expanded', String(open));
        persistSidebarState(open); if (open) renderFeed();
    }
    toggle.addEventListener('click', function () { setOpen(!isOpen()); });
    sidebar.querySelector('.bd-annotation-sidebar-close').addEventListener('click', function () { setOpen(false); });
    setOpen(readSidebarState());

    function nodeOffset(node) { var w = document.createTreeWalker(content, NodeFilter.SHOW_TEXT), offset = 0, current; while ((current = w.nextNode())) { if (current === node) return offset; offset += current.textContent.length; } return offset; }
    function selectionData(range) { return {startOffset: nodeOffset(range.startContainer) + range.startOffset, endOffset: nodeOffset(range.endContainer) + range.endOffset, selectedText: range.toString().trim()}; }
    function unwrap() { content.querySelectorAll('mark.bd-annotation-highlight').forEach(function (mark) { var parent = mark.parentNode; while (mark.firstChild) parent.insertBefore(mark.firstChild, mark); parent.removeChild(mark); parent.normalize(); }); }
    function markRange(ann) {
        var w = document.createTreeWalker(content, NodeFilter.SHOW_TEXT), offset = 0, node, startNode, endNode, start, end;
        while ((node = w.nextNode())) { var len = node.textContent.length; if (!startNode && ann.startOffset < offset + len) { startNode = node; start = ann.startOffset - offset; } if (ann.endOffset <= offset + len) { endNode = node; end = ann.endOffset - offset; break; } offset += len; }
        if (!startNode || !endNode) return;
        try { var range = document.createRange(); range.setStart(startNode, Math.max(0, start)); range.setEnd(endNode, Math.max(start, Math.min(end, endNode.textContent.length))); var mark = document.createElement('mark'); mark.className = 'bd-annotation-highlight bd-annotation-color-' + ann.color; mark.dataset.id = ann.id; range.surroundContents(mark); } catch (ignored) { /* 跨结构选区不改写正文 DOM */ }
    }
    function renderMarks() { unwrap(); annotations.slice().sort(function (a, b) { return a.startOffset - b.startOffset; }).forEach(markRange); }
    function renderFeed() {
        feed.replaceChildren();
        if (!annotations.length) { var empty = document.createElement('p'); empty.className = 'bd-annotation-empty'; empty.textContent = '选中文章文字，即可添加划线或评论。'; feed.appendChild(empty); return; }
        annotations.forEach(function (ann) {
            var item = document.createElement('article'); item.className = 'bd-annotation-feed-item'; item.dataset.id = ann.id;
            var quote = document.createElement('blockquote'); quote.textContent = ann.selectedText; item.appendChild(quote);
            var body = document.createElement('p'); body.className = 'bd-annotation-feed-text'; body.textContent = ann.annotationText || '仅划线'; item.appendChild(body);
            var meta = document.createElement('div'); meta.className = 'bd-annotation-feed-meta'; meta.textContent = ann.visibility === 'PRIVATE' ? '仅自己可见' : '公开'; item.appendChild(meta);
            if (ann.ownedByCurrentVisitor) { var actions = document.createElement('div'); actions.className = 'bd-annotation-feed-actions'; var edit = document.createElement('button'); edit.type = 'button'; edit.textContent = '编辑'; edit.onclick = function () { editAnnotation(ann); }; var remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '删除'; remove.onclick = function () { removeAnnotation(ann.id); }; actions.append(edit, remove); item.appendChild(actions); }
            feed.appendChild(item);
        });
    }
    function selectColor(next) { color = next; sidebar.querySelectorAll('[data-bd-annotation-color]').forEach(function (button) { button.classList.toggle('bd-annotation-color-selected', button.dataset.bdAnnotationColor === color); }); }
    sidebar.querySelectorAll('[data-bd-annotation-color]').forEach(function (button) { button.addEventListener('click', function () { selectColor(button.dataset.bdAnnotationColor); }); });
    function openComposer(data, existing) {
        selected = data; composer.hidden = false; composer.dataset.editId = existing ? existing.id : '';
        composer.querySelector('.bd-annotation-composer-quote').textContent = data.selectedText;
        composer.querySelector('.bd-annotation-composer-text').value = existing ? (existing.annotationText || '') : '';
        composer.querySelector('.bd-annotation-visibility').value = existing ? existing.visibility : 'PRIVATE';
        visibilityChanged = !!existing; selectColor(existing ? existing.color : 'yellow'); setOpen(true);
    }
    function closeComposer() { composer.hidden = true; selected = null; delete composer.dataset.editId; }
    sidebar.querySelector('.bd-annotation-composer-cancel').addEventListener('click', closeComposer);
    composer.querySelector('.bd-annotation-visibility').addEventListener('change', function () { visibilityChanged = true; });
    composer.querySelector('.bd-annotation-composer-text').addEventListener('input', function (event) { if (!visibilityChanged) composer.querySelector('.bd-annotation-visibility').value = event.target.value.trim() ? 'PUBLIC' : 'PRIVATE'; });
    function saveComposer() {
        if (!selected) return; var text = composer.querySelector('.bd-annotation-composer-text').value.trim(); var visibility = composer.querySelector('.bd-annotation-visibility').value; var editId = composer.dataset.editId;
        var promise = editId ? api('/' + editId, 'PATCH', {annotationText: text || null, visibility: visibility}) : api('', 'POST', {selectedText: selected.selectedText.slice(0, 500), annotationText: text || null, color: color, visibility: visibility, startOffset: selected.startOffset, endOffset: selected.endOffset});
        promise.then(function (saved) { var index = annotations.findIndex(function (annotation) { return String(annotation.id) === String(saved.id); }); if (index < 0) annotations.push(saved); else annotations[index] = saved; renderMarks(); renderFeed(); closeComposer(); window.getSelection().removeAllRanges(); }).catch(function () { composer.querySelector('.bd-annotation-composer-save').textContent = '失败，重试'; });
    }
    sidebar.querySelector('.bd-annotation-composer-save').addEventListener('click', saveComposer);
    function editAnnotation(ann) { openComposer({selectedText: ann.selectedText}, ann); }
    function removeAnnotation(id) { api('/' + id, 'DELETE').then(function () { annotations = annotations.filter(function (ann) { return String(ann.id) !== String(id); }); renderMarks(); renderFeed(); }); }
    function hidePopup() { if (popup) popup.classList.remove('bd-annotation-popup-open'); }
    function showPrimaryMenu() {
        popup.innerHTML = '<button type="button" data-copy>复制</button><button type="button" data-highlight>划线</button><button type="button" data-comment>评论</button>';
        popup.querySelector('[data-copy]').onclick = function () { navigator.clipboard && navigator.clipboard.writeText(selected.selectedText); hidePopup(); };
        popup.querySelector('[data-highlight]').onclick = showHighlightMenu;
        popup.querySelector('[data-comment]').onclick = function () { openComposer(selected); hidePopup(); };
    }
    function showHighlightMenu() {
        popup.innerHTML = '<button type="button" data-back>‹ 返回</button><button type="button" data-color="yellow" aria-label="黄色划线">黄</button><button type="button" data-color="red" aria-label="红色划线">红</button><button type="button" data-color="green" aria-label="绿色划线">绿</button><button type="button" data-color="blue" aria-label="蓝色划线">蓝</button><button type="button" data-cancel>取消</button>';
        popup.querySelector('[data-back]').onclick = showPrimaryMenu;
        popup.querySelector('[data-cancel]').onclick = hidePopup;
        popup.querySelectorAll('[data-color]').forEach(function (button) { button.onclick = function () { color = button.dataset.color; createHighlight(); }; });
    }
    function buildPopup() { var el = document.createElement('div'); el.className = 'bd-annotation-popup'; document.body.appendChild(el); popup = el; showPrimaryMenu(); return el; }
    function showPopup(range) { if (!popup) popup = buildPopup(); selected = selectionData(range); popup.classList.add('bd-annotation-popup-open'); var rect = range.getBoundingClientRect(); popup.style.left = Math.max(8, rect.left) + 'px'; popup.style.top = Math.max(8, rect.bottom + 8) + 'px'; }
    function createHighlight() { if (!selected) return; api('', 'POST', {selectedText: selected.selectedText.slice(0, 500), annotationText: null, color: color, visibility: 'PRIVATE', startOffset: selected.startOffset, endOffset: selected.endOffset}).then(function (saved) { annotations.push(saved); renderMarks(); if (isOpen()) renderFeed(); hidePopup(); window.getSelection().removeAllRanges(); }); }
    document.addEventListener('mouseup', function (event) { if (sidebar.contains(event.target) || (popup && popup.contains(event.target))) return; var selection = window.getSelection(); if (!selection.rangeCount) return; var range = selection.getRangeAt(0); if (range.collapsed || !range.toString().trim() || !content.contains(range.commonAncestorContainer)) return; var data = selectionData(range); if (isOpen()) openComposer(data); else showPopup(range); });
    content.addEventListener('click', function (event) { var mark = event.target.closest && event.target.closest('mark.bd-annotation-highlight'); if (!mark) return; var ann = annotations.find(function (item) { return String(item.id) === mark.dataset.id; }); if (ann) { setOpen(true); var item = feed.querySelector('[data-id="' + ann.id + '"]'); if (item) item.scrollIntoView({block: 'nearest'}); } });
    renderMarks();
})();
