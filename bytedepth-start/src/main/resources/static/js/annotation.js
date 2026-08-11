/* 文章批注：选中文本 → 弹框 → 高亮 → 悬停显示 → 删除（仅作者） */
(function () {
    'use strict';

    var article = document.getElementById('post-article');
    var content = article ? article.querySelector('.content') : null;
    if (!content) return;

    var annotations = window.__ANNOTATIONS__ || [];
    var currentUserId = window.__CURRENT_USER_ID__ !== undefined
        ? window.__CURRENT_USER_ID__ : null;
    var csrfMeta = document.querySelector('meta[name="_csrf"]');
    var csrfToken = csrfMeta ? csrfMeta.content : '';

    var popup = null;
    var tooltip = null;
    var lastSelection = null;   // { start, end, text }
    var selectedColor = 'yellow';

    /* ── 偏移计算：文本节点在 content.textContent 中的起始偏移 ── */
    function getNodeOffset(node) {
        var walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT);
        var offset = 0;
        var current;
        while ((current = walker.nextNode())) {
            if (current === node) return offset;
            offset += current.textContent.length;
        }
        return offset;
    }

    function selectionOffsets(range) {
        return {
            start: getNodeOffset(range.startContainer) + range.startOffset,
            end: getNodeOffset(range.endContainer) + range.endOffset,
            text: range.toString()
        };
    }

    /* ── 高亮渲染（幂等） ─────────────────────────── */
    function unwrapAll() {
        content.querySelectorAll('mark.annotation-highlight').forEach(function (m) {
            var parent = m.parentNode;
            while (m.firstChild) parent.insertBefore(m.firstChild, m);
            parent.removeChild(m);
            parent.normalize();
        });
    }

    function highlightRange(start, end, cls, id, userId, annText) {
        var walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT);
        var offset = 0;
        var current;
        var startNode = null, startOff = 0, endNode = null, endOff = 0;
        while ((current = walker.nextNode())) {
            var len = current.textContent.length;
            if (startNode === null && start < offset + len) {
                startNode = current;
                startOff = start - offset;
            }
            if (end <= offset + len) {
                endNode = current;
                endOff = end - offset;
                break;
            }
            offset += len;
        }
        if (!startNode || !endNode) return;
        try {
            var range = document.createRange();
            range.setStart(startNode, Math.max(0, startOff));
            range.setEnd(endNode, Math.max(startOff, Math.min(endOff, endNode.textContent.length)));
            var mark = document.createElement('mark');
            mark.className = 'annotation-highlight a-' + cls;
            mark.dataset.id = String(id);
            mark.dataset.userId = String(userId);
            mark.dataset.ann = annText;
            range.surroundContents(mark);
        } catch (e) {
            /* 跨元素选区兜底跳过，避免破坏 DOM */
        }
    }

    function applyAnnotations() {
        unwrapAll();
        annotations.slice().sort(function (a, b) { return a.startOffset - b.startOffset; })
            .forEach(function (ann) {
                highlightRange(ann.startOffset, ann.endOffset, ann.color,
                    ann.id, ann.userId, ann.annotationText);
            });
    }

    /* ── 弹框 ─────────────────────────────────────── */
    function buildPopup() {
        var div = document.createElement('div');
        div.className = 'annotation-popup';
        div.innerHTML =
            '<div class="ap-quote"></div>' +
            '<div class="ap-colors">' +
            '<span class="ap-color c-red" data-color="red" title="重点"></span>' +
            '<span class="ap-color c-yellow active" data-color="yellow" title="疑问"></span>' +
            '<span class="ap-color c-green" data-color="green" title="赞同"></span>' +
            '<span class="ap-color c-blue" data-color="blue" title="补充"></span>' +
            '</div>' +
            '<textarea placeholder="写下你的批注..." maxlength="2000"></textarea>' +
            '<div class="ap-actions">' +
            '<button class="ap-cancel" type="button">取消</button>' +
            '<button class="ap-save" type="button">保存</button>' +
            '</div>';
        document.body.appendChild(div);

        div.querySelectorAll('.ap-color').forEach(function (c) {
            c.addEventListener('click', function () {
                div.querySelectorAll('.ap-color').forEach(function (x) { x.classList.remove('active'); });
                c.classList.add('active');
                selectedColor = c.dataset.color;
            });
        });
        div.querySelector('.ap-cancel').addEventListener('click', closePopup);
        div.querySelector('.ap-save').addEventListener('click', saveAnnotation);
        return div;
    }

    function showPopup(range) {
        if (!popup) popup = buildPopup();
        lastSelection = selectionOffsets(range);
        popup.querySelector('.ap-quote').textContent = lastSelection.text;
        popup.querySelector('textarea').value = '';
        popup.classList.add('open');
        positionElement(popup, range);
    }

    function closePopup() {
        if (!popup) return;
        popup.classList.remove('open');
        lastSelection = null;
    }

    function saveAnnotation() {
        if (!lastSelection) return;
        var annText = popup.querySelector('textarea').value.trim();
        if (!annText) {
            popup.querySelector('textarea').focus();
            return;
        }
        var payload = {
            selectedText: lastSelection.text.slice(0, 500),
            annotationText: annText.slice(0, 2000),
            color: selectedColor,
            startOffset: lastSelection.start,
            endOffset: lastSelection.end
        };
        var saveBtn = popup.querySelector('.ap-save');
        saveBtn.disabled = true;
        fetch(window.location.pathname + '/annotations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
            body: JSON.stringify(payload)
        }).then(function (r) {
            if (!r.ok) throw new Error('create failed');
            return r.json();
        }).then(function (created) {
            annotations.push(created);
            applyAnnotations();
            closePopup();
            window.getSelection().removeAllRanges();
        }).catch(function () {
            saveBtn.disabled = false;
            saveBtn.textContent = '失败，重试';
        });
    }

    /* ── tooltip ───────────────────────────────────── */
    function buildTooltip() {
        var div = document.createElement('div');
        div.className = 'annotation-tooltip';
        div.innerHTML =
            '<div class="at-quote"></div>' +
            '<div class="at-body"></div>' +
            '<div class="at-meta">' +
            '<span class="at-author"></span>' +
            '<button class="at-delete" type="button">删除</button>' +
            '</div>';
        document.body.appendChild(div);
        return div;
    }

    function showTooltip(mark) {
        if (!tooltip) tooltip = buildTooltip();
        tooltip.querySelector('.at-quote').textContent = mark.textContent;
        tooltip.querySelector('.at-body').textContent = mark.dataset.ann || '';
        var delBtn = tooltip.querySelector('.at-delete');
        if (String(mark.dataset.userId) === String(currentUserId)) {
            delBtn.style.display = '';
            delBtn.onclick = function () { deleteAnnotation(mark); };
        } else {
            delBtn.style.display = 'none';
        }
        tooltip.classList.add('open');
        positionElement(tooltip, mark);
    }

    function closeTooltip() {
        if (!tooltip) return;
        tooltip.classList.remove('open');
    }

    function deleteAnnotation(mark) {
        var id = mark.dataset.id;
        fetch(window.location.pathname + '/annotations/' + id, {
            method: 'DELETE',
            headers: { 'X-CSRF-TOKEN': csrfToken }
        }).then(function (r) {
            if (!r.ok) throw new Error('delete failed');
            var idx = annotations.findIndex(function (a) { return String(a.id) === String(id); });
            if (idx >= 0) annotations.splice(idx, 1);
            applyAnnotations();
            closeTooltip();
        });
    }

    /* ── 定位 ─────────────────────────────────────── */
    function positionElement(el, anchor) {
        var rect = anchor.getBoundingClientRect();
        var w = el.offsetWidth || 280;
        var left = Math.max(8, Math.min(window.innerWidth - w - 8, rect.left + rect.width / 2 - w / 2));
        var top = rect.top - el.offsetHeight - 8;
        if (top < 8) top = rect.bottom + 8;
        el.style.left = left + 'px';
        el.style.top = top + 'px';
    }

    /* ── 事件绑定 ─────────────────────────────────── */
    document.addEventListener('mouseup', function (e) {
        if (popup && popup.contains(e.target)) return;
        if (tooltip && tooltip.contains(e.target)) return;
        closeTooltip();
        var sel = window.getSelection();
        if (!sel.rangeCount) { closePopup(); return; }
        var range = sel.getRangeAt(0);
        if (range.collapsed || range.toString().trim().length === 0) { closePopup(); return; }
        if (!content.contains(range.commonAncestorContainer)) { closePopup(); return; }
        if (currentUserId === null) return;   // 未登录不弹创建框
        showPopup(range);
    });

    // mark 悬停显示 tooltip（事件委托）
    content.addEventListener('mouseover', function (e) {
        var mark = e.target.closest ? e.target.closest('mark.annotation-highlight') : null;
        if (mark) showTooltip(mark);
    });
    content.addEventListener('mouseout', function (e) {
        var mark = e.target.closest ? e.target.closest('mark.annotation-highlight') : null;
        if (mark) closeTooltip();
    });

    document.addEventListener('click', function (e) {
        if (tooltip && !tooltip.contains(e.target) && !(e.target.closest && e.target.closest('mark.annotation-highlight'))) {
            closeTooltip();
        }
    });
    document.addEventListener('scroll', function () { closePopup(); closeTooltip(); }, true);

    /* ── 初始化 ───────────────────────────────────── */
    applyAnnotations();
})();
