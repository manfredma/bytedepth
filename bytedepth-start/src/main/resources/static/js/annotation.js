/* 阅读批注：匿名归属由 HttpOnly Cookie 在服务端维护。 */
(function () {
    'use strict';

    const article = document.getElementById('post-article');
    const content = article && article.querySelector('.content');
    const sidebar = document.getElementById('bd-annotation-sidebar');
    const toggle = document.getElementById('bd-annotation-sidebar-toggle');
    if (!content || !sidebar || !toggle) {
        return;
    }
    const commentOutlineLayer = document.createElement('div');
    commentOutlineLayer.className = 'bd-annotation-comment-outline-layer';
    commentOutlineLayer.setAttribute('aria-hidden', 'true');
    article.appendChild(commentOutlineLayer);

    let annotations = window.__ANNOTATIONS__ || [];
    let selected = null;
    let color = 'yellow';
    let visibilityChanged = false;
    let popup;
    let mobileNote;
    // 当前在侧栏中聚焦展示的批注 id；用于判断点击同一批注 trigger 时是否应回收侧栏。
    let activeAnnotationId = null;

    const csrf = (document.querySelector('meta[name="_csrf"]') || {}).content || '';
    const composer = sidebar.querySelector('.bd-annotation-composer');
    const feed = sidebar.querySelector('.bd-annotation-feed');
    const sidebarCount = sidebar.querySelector('.bd-annotation-comment-count');
    const toolbarCount = toggle.querySelector('.bd-annotation-toolbar-count');
    const storageKey = 'bd.annotation.sidebar.open';

    function isMobile() {
        return Boolean(window.matchMedia && window.matchMedia('(max-width: 768px)').matches);
    }

    function readSidebarState() {
        try {
            return localStorage.getItem(storageKey) === 'true';
        } catch {
            return false;
        }
    }

    function persistSidebarState(open) {
        try {
            localStorage.setItem(storageKey, String(open));
        } catch {
            // 存储不可用时仍允许当前页面使用批注。
        }
    }

    function api(path, method, payload) {
        return fetch(window.location.pathname + '/annotations' + path, {
            method,
            headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf},
            body: payload ? JSON.stringify(payload) : undefined
        }).then(response => {
            if (!response.ok) {
                throw new Error('annotation request failed');
            }
            return response.status === 204 ? null : response.json();
        });
    }

    function comments() {
        return annotations.filter(annotation => annotation.annotationText && annotation.annotationText.trim());
    }

    function updateCommentCount() {
        const count = comments().length;
        if (sidebarCount) {
            sidebarCount.textContent = String(count);
        }
        if (toolbarCount) {
            toolbarCount.textContent = count > 99 ? '99+' : String(count);
            toolbarCount.hidden = count === 0;
        }
    }

    function isOpen() {
        return sidebar.classList.contains('bd-annotation-sidebar-open');
    }

    function setOpen(open) {
        sidebar.classList.toggle('bd-annotation-sidebar-open', open);
        article.classList.toggle('bd-annotation-reading-layout-open', open);
        sidebar.setAttribute('aria-hidden', String(!open));
        toggle.setAttribute('aria-expanded', String(open));
        document.body.classList.toggle('bd-annotation-comments-open', open);
        persistSidebarState(open);
        if (!open) {
            activeAnnotationId = null;
        }
        // 侧栏开关会改变正文的 Grid 布局；下一帧后评注框必须按新坐标重绘。
        requestAnimationFrame(renderCommentOutlines);
        if (open) {
            renderFeed();
        }
    }

    function nodeOffset(node) {
        const walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT);
        let offset = 0;
        let current;
        while ((current = walker.nextNode())) {
            if (current.parentElement?.closest('.bd-annotation-comment-trigger')) {
                continue;
            }
            if (current === node) {
                return offset;
            }
            offset += current.textContent.length;
        }
        return offset;
    }

    function selectionData(range) {
        return {
            startOffset: nodeOffset(range.startContainer) + range.startOffset,
            endOffset: nodeOffset(range.endContainer) + range.endOffset,
            selectedText: range.toString().trim()
        };
    }

    function isContentNode(node) {
        return node === content || content.contains(node);
    }

    function hasActiveContentSelection() {
        const selection = window.getSelection();
        if (!selection.rangeCount) {
            return false;
        }
        const range = selection.getRangeAt(0);
        return !range.collapsed && Boolean(range.toString().trim())
            && isContentNode(range.startContainer) && isContentNode(range.endContainer);
    }

    function unwrapMarks() {
        commentOutlineLayer.replaceChildren();
        content.querySelectorAll('mark.bd-annotation-highlight').forEach(mark => {
            const parent = mark.parentNode;
            while (mark.firstChild) {
                parent.insertBefore(mark.firstChild, mark);
            }
            parent.removeChild(mark);
            parent.normalize();
        });
    }

    function hasComment(annotation) {
        return Boolean(annotation.annotationText && annotation.annotationText.trim());
    }

    function createMarker(annotation) {
        const mark = document.createElement('mark');
        mark.className = `bd-annotation-highlight bd-annotation-color-${annotation.color}`
            + (hasComment(annotation) ? ' bd-annotation-has-comment' : '');
        mark.dataset.id = annotation.id;
        return mark;
    }

    function createCommentTrigger(annotation) {
        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'bd-annotation-comment-trigger';
        trigger.textContent = '评注';
        trigger.setAttribute('aria-label', '打开阅读批注');
        trigger.addEventListener('mouseup', event => event.stopPropagation());
        trigger.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            // 侧栏已打开且点击的是当前同一批注时才回收；切换到不同批注时保持打开并滚动定位。
            const sameAnnotation = isOpen() && activeAnnotationId === annotation.id;
            setOpen(!sameAnnotation);
            activeAnnotationId = sameAnnotation ? null : annotation.id;
            updateActiveItem();
            const item = feed.querySelector(`[data-id="${annotation.id}"]`);
            if (isOpen() && item) {
                item.scrollIntoView?.({block: 'nearest', behavior: 'smooth'});
            }
        });
        return trigger;
    }

    function restoreSelection(data) {
        const range = rangeForOffsets(data);
        if (!range) {
            return;
        }
        try {
            const selection = window.getSelection();
            selection.removeAllRanges();
            selection.addRange(range);
        } catch {
            // 正文结构变化后无法精确恢复选择时，不影响已保存的批注。
        }
    }

    function rangeForOffsets(data) {
        const walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT);
        let offset = 0;
        let node;
        let startNode;
        let endNode;
        let start;
        let end;
        while ((node = walker.nextNode())) {
            const length = node.textContent.length;
            if (!startNode && data.startOffset < offset + length) {
                startNode = node;
                start = data.startOffset - offset;
            }
            if (data.endOffset <= offset + length) {
                endNode = node;
                end = data.endOffset - offset;
                break;
            }
            offset += length;
        }
        if (!startNode || !endNode) {
            return null;
        }
        try {
            const range = document.createRange();
            range.setStart(startNode, Math.max(0, start));
            range.setEnd(endNode, Math.max(start, Math.min(end, endNode.textContent.length)));
            return range;
        } catch {
            return null;
        }
    }

    function commentRows(range) {
        const rects = Array.from(range.getClientRects()).filter(rect => rect.width > 0 && rect.height > 0);
        if (!rects.length) {
            const fallback = range.getBoundingClientRect();
            if (fallback.width > 0 && fallback.height > 0) {
                rects.push(fallback);
            }
        }
        return rects.sort((left, right) => left.top - right.top || left.left - right.left)
            .reduce((rows, rect) => {
                const row = rows.at(-1);
                if (row && Math.abs(row.top - rect.top) < 2 && Math.abs(row.bottom - rect.bottom) < 2) {
                    row.left = Math.min(row.left, rect.left);
                    row.right = Math.max(row.right, rect.right);
                    return rows;
                }
                rows.push({left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom});
                return rows;
            }, []);
    }

    function renderCommentOutlines() {
        commentOutlineLayer.replaceChildren();
        const renderedRanges = new Set();
        annotations.filter(hasComment).forEach(annotation => {
            const rangeKey = `${annotation.startOffset}:${annotation.endOffset}`;
            if (renderedRanges.has(rangeKey)) {
                return;
            }
            renderedRanges.add(rangeKey);
            const range = rangeForOffsets(annotation);
            if (!range) {
                return;
            }
            commentRows(range).forEach((row, index) => {
                // 为首行的“评注”标签预留垂直槽位，标签不覆盖正文文字。
                const labelGutter = index === 0 ? 9 : 0;
                const outline = document.createElement('div');
                outline.className = `bd-annotation-comment-outline bd-annotation-color-${annotation.color}`;
                outline.dataset.annotationId = annotation.id;
                outline.dataset.textTop = String(row.top);
                outline.style.left = `${row.left}px`;
                outline.style.top = `${row.top - labelGutter}px`;
                outline.style.width = `${row.right - row.left}px`;
                outline.style.height = `${row.bottom - row.top + labelGutter}px`;
                if (index === 0) {
                    outline.appendChild(createCommentTrigger(annotation));
                }
                commentOutlineLayer.appendChild(outline);
            });
        });
    }

    function renderMarks() {
        unwrapMarks();
        const walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT);
        const textNodes = [];
        let node;
        while ((node = walker.nextNode())) {
            textNodes.push(node);
        }
        let offset = 0;
        textNodes.forEach(textNode => {
            const text = textNode.textContent;
            const endOffset = offset + text.length;
            const boundaries = new Set([offset, endOffset]);
            annotations.forEach(annotation => {
                if (annotation.startOffset > offset && annotation.startOffset < endOffset) {
                    boundaries.add(annotation.startOffset);
                }
                if (annotation.endOffset > offset && annotation.endOffset < endOffset) {
                    boundaries.add(annotation.endOffset);
                }
            });
            const positions = Array.from(boundaries).sort((left, right) => left - right);
            const fragment = document.createDocumentFragment();
            for (let index = 0; index < positions.length - 1; index += 1) {
                const segmentStart = positions[index];
                const segmentEnd = positions[index + 1];
                const applicable = annotations.filter(annotation => annotation.startOffset <= segmentStart
                    && annotation.endOffset >= segmentEnd);
                let decorated = document.createTextNode(text.slice(segmentStart - offset, segmentEnd - offset));
                applicable.slice().reverse().forEach(annotation => {
                    const marker = createMarker(annotation);
                    marker.appendChild(decorated);
                    decorated = marker;
                });
                fragment.appendChild(decorated);
            }
            textNode.replaceWith(fragment);
            offset = endOffset;
        });
        renderCommentOutlines();
    }

    function layoutFeed() {
        const items = Array.from(feed.querySelectorAll('.bd-annotation-feed-item'));
        if (!items.length) {
            return;
        }
        // 仅在宽屏 sticky 布局下让卡片随划线滚动、在 feed 顶部横线处离场；
        // 中屏桌面（侧栏位于正文下方）的 feed 与正文分属不同区域，划线必然落在 feed 顶部之上，
        // 若沿用 feedRect.top 作为离场边界会把所有卡片整体隐藏，故展开为常规可读列表。
        if (getComputedStyle(sidebar).position !== 'sticky') {
            items.forEach(item => {
                item.hidden = false;
                item.style.top = '';
                item.style.opacity = '';
            });
            const flatSpacer = feed.querySelector('.bd-annotation-feed-spacer');
            if (flatSpacer) {
                flatSpacer.style.height = '';
            }
            return;
        }
        const feedRect = feed.getBoundingClientRect();
        const composerHeight = composer.hidden ? 0 : composer.offsetHeight + 28;
        // 评论内容区顶部的分隔线就是卡片的视觉离场边界，不能使用浏览器或导航栏顶部。
        const viewportTop = feedRect.top;
        let nextTop = Number.NEGATIVE_INFINITY;
        items.forEach(item => {
            const mark = content.querySelector(`mark[data-id="${item.dataset.id}"]`);
            const markRect = mark && mark.getBoundingClientRect();
            const visible = markRect && markRect.bottom > viewportTop && markRect.top < window.innerHeight;
            item.hidden = !visible;
            if (!visible) {
                item.style.removeProperty('opacity');
                return;
            }
            const edgeDistance = Math.min(markRect.bottom - viewportTop, window.innerHeight - markRect.top);
            item.style.opacity = String(Math.max(0, Math.min(1, edgeDistance / 48)));
            const anchorTop = markRect.top - feedRect.top + feed.scrollTop;
            const top = Math.max(anchorTop, nextTop);
            item.style.top = `${top}px`;
            nextTop = top + Math.max(item.offsetHeight, 94) + 12;
        });
        const spacer = feed.querySelector('.bd-annotation-feed-spacer');
        spacer.style.height = `${(Number.isFinite(nextTop) ? nextTop : 0) + composerHeight}px`;
    }

    function renderFeed() {
        feed.replaceChildren();
        const commentItems = comments();
        updateCommentCount();
        if (!commentItems.length) {
            const empty = document.createElement('p');
            empty.className = 'bd-annotation-empty';
            empty.textContent = '还没有划线评论。选中文字后，可以写下第一条想法。';
            feed.appendChild(empty);
            return;
        }
        commentItems.forEach(annotation => {
            const item = document.createElement('article');
            item.className = 'bd-annotation-feed-item';
            item.dataset.id = annotation.id;
            item.dataset.color = annotation.color;

            const quote = document.createElement('blockquote');
            quote.textContent = annotation.selectedText;
            item.appendChild(quote);

            const text = document.createElement('p');
            text.className = 'bd-annotation-feed-text';
            text.textContent = annotation.annotationText;
            item.appendChild(text);

            const footer = document.createElement('footer');
            footer.className = 'bd-annotation-feed-footer';

            const meta = document.createElement('span');
            meta.className = 'bd-annotation-feed-meta';
            meta.textContent = annotation.visibility === 'PRIVATE' ? '仅自己可见' : '公开评论';
            footer.appendChild(meta);

            if (annotation.ownedByCurrentVisitor) {
                const actions = document.createElement('div');
                actions.className = 'bd-annotation-feed-actions';
                const edit = document.createElement('button');
                edit.type = 'button';
                edit.textContent = '编辑';
                edit.addEventListener('click', () => openInlineEditor(annotation, item));
                const remove = document.createElement('button');
                remove.type = 'button';
                remove.textContent = '删除';
                remove.addEventListener('click', () => removeAnnotation(annotation.id, () => {
                    remove.textContent = '删除失败，请重试';
                    window.setTimeout(() => { remove.textContent = '删除'; }, 2000);
                }));
                actions.append(edit, remove);
                footer.appendChild(actions);
            }
            item.appendChild(footer);
            feed.appendChild(item);
        });
        const spacer = document.createElement('div');
        spacer.className = 'bd-annotation-feed-spacer';
        spacer.setAttribute('aria-hidden', 'true');
        feed.appendChild(spacer);
        updateActiveItem();
        requestAnimationFrame(layoutFeed);
    }

    function updateActiveItem() {
        feed.querySelectorAll('.bd-annotation-feed-item-active').forEach(item => {
            item.classList.remove('bd-annotation-feed-item-active');
        });
        if (activeAnnotationId !== null) {
            const active = feed.querySelector(`.bd-annotation-feed-item[data-id="${activeAnnotationId}"]`);
            if (active) {
                active.classList.add('bd-annotation-feed-item-active');
            }
        }
    }

    function openInlineEditor(annotation, item) {
        item.replaceChildren();
        item.classList.add('bd-annotation-feed-item-editing');

        const quote = document.createElement('blockquote');
        quote.textContent = annotation.selectedText;
        item.appendChild(quote);

        const input = document.createElement('textarea');
        input.className = 'bd-annotation-inline-editor-text';
        input.maxLength = 2000;
        input.value = annotation.annotationText || '';
        input.setAttribute('aria-label', '编辑评注内容');
        item.appendChild(input);

        const footer = document.createElement('footer');
        footer.className = 'bd-annotation-feed-footer bd-annotation-inline-editor-footer';
        const visibility = document.createElement('select');
        visibility.className = 'bd-annotation-inline-editor-visibility';
        visibility.setAttribute('aria-label', '评论可见范围');
        visibility.innerHTML = '<option value="PUBLIC">公开</option><option value="PRIVATE">仅自己可见</option>';
        visibility.value = annotation.visibility;

        const actions = document.createElement('div');
        actions.className = 'bd-annotation-feed-actions';
        const cancel = document.createElement('button');
        cancel.type = 'button';
        cancel.textContent = '取消';
        cancel.addEventListener('click', renderFeed);
        const save = document.createElement('button');
        save.type = 'button';
        save.className = 'bd-annotation-inline-editor-save';
        save.textContent = '保存';
        save.addEventListener('click', () => {
            const annotationText = input.value.trim();
            api(`/${annotation.id}`, 'PATCH', {annotationText: annotationText || null, visibility: visibility.value})
                .then(saved => {
                    const index = annotations.findIndex(itemAnnotation => String(itemAnnotation.id) === String(saved.id));
                    if (index >= 0) {
                        annotations[index] = saved;
                    }
                    renderMarks();
                    renderFeed();
                })
                .catch(() => {
                    save.textContent = '保存失败，请重试';
                });
        });
        actions.append(cancel, save);
        footer.append(visibility, actions);
        item.appendChild(footer);
        requestAnimationFrame(layoutFeed);
        input.focus();
    }

    function selectColor(nextColor) {
        color = nextColor;
        sidebar.querySelectorAll('[data-bd-annotation-color]').forEach(button => {
            button.classList.toggle('bd-annotation-color-selected', button.dataset.bdAnnotationColor === color);
        });
    }

    function openComposer(data, existing) {
        selected = data;
        composer.hidden = false;
        composer.dataset.editId = existing ? existing.id : '';
        composer.querySelector('.bd-annotation-composer-quote').textContent = data.selectedText;
        composer.querySelector('.bd-annotation-composer-text').value = existing ? existing.annotationText || '' : '';
        composer.querySelector('.bd-annotation-visibility').value = existing ? existing.visibility : 'PRIVATE';
        visibilityChanged = Boolean(existing);
        selectColor(existing ? existing.color : 'yellow');
        setOpen(true);
        requestAnimationFrame(() => composer.querySelector('.bd-annotation-composer-text').focus());
    }

    function closeComposer() {
        composer.hidden = true;
        selected = null;
        delete composer.dataset.editId;
        if (isOpen()) {
            renderFeed();
        }
    }

    function saveComposer() {
        if (!selected) {
            return;
        }
        const annotatedSelection = selected;
        const annotationText = composer.querySelector('.bd-annotation-composer-text').value.trim();
        const visibility = composer.querySelector('.bd-annotation-visibility').value;
        const editId = composer.dataset.editId;
        const request = editId
            ? api(`/${editId}`, 'PATCH', {annotationText: annotationText || null, visibility})
            : api('', 'POST', {
                selectedText: selected.selectedText.slice(0, 500),
                annotationText: annotationText || null,
                color,
                visibility,
                startOffset: selected.startOffset,
                endOffset: selected.endOffset
            });
        request.then(saved => {
            const index = annotations.findIndex(annotation => String(annotation.id) === String(saved.id));
            if (index < 0) {
                annotations.push(saved);
            } else {
                annotations[index] = saved;
            }
            renderMarks();
            closeComposer();
            restoreSelection(annotatedSelection);
            updateCommentCount();
        }).catch(() => {
            composer.querySelector('.bd-annotation-composer-save').textContent = '保存失败，请重试';
        });
    }

    function removeAnnotation(id, onFailure) {
        api(`/${id}`, 'DELETE').then(() => {
            annotations = annotations.filter(annotation => String(annotation.id) !== String(id));
            renderMarks();
            renderFeed();
        }).catch(() => {
            // 删除失败时不移除本地标记，避免与服务端不一致；向用户反馈。
            if (typeof onFailure === 'function') {
                onFailure();
            }
        });
    }

    function hidePopup() {
        if (popup) {
            popup.classList.remove('bd-annotation-popup-open');
        }
    }

    function eventOccurredInside(event, element) {
        return Boolean(element && event.composedPath().includes(element));
    }

    function copyWithFallback(text) {
        const textArea = document.createElement('textarea');
        textArea.value = text;
        textArea.setAttribute('readonly', '');
        textArea.style.cssText = 'position:fixed;opacity:0;pointer-events:none';
        document.body.appendChild(textArea);
        textArea.select();
        try {
            return typeof document.execCommand === 'function' && document.execCommand('copy');
        } catch {
            return false;
        } finally {
            textArea.remove();
        }
    }

    function copySelection(text) {
        if (!navigator.clipboard || typeof navigator.clipboard.writeText !== 'function') {
            return Promise.resolve(copyWithFallback(text));
        }
        return navigator.clipboard.writeText(text)
            .then(() => true)
            .catch(() => copyWithFallback(text));
    }

    function showCopyResult(copied) {
        const result = document.createElement('span');
        result.className = 'bd-annotation-copy-result';
        result.textContent = copied ? '已复制到剪贴板' : '复制失败，请手动复制';
        popup.replaceChildren(result);
        window.setTimeout(() => {
            if (popup.contains(result)) {
                hidePopup();
            }
        }, 1200);
    }

    function showMobileComment(annotation, mark) {
        if (!annotation.annotationText || !annotation.annotationText.trim()) {
            return;
        }
        if (!mobileNote) {
            mobileNote = document.createElement('aside');
            mobileNote.className = 'bd-annotation-mobile-note';
            document.body.appendChild(mobileNote);
        }
        mobileNote.textContent = annotation.annotationText;
        const rect = mark.getBoundingClientRect();
        mobileNote.style.left = `${Math.max(12, Math.min(rect.left, window.innerWidth - 316))}px`;
        mobileNote.style.top = `${Math.min(window.innerHeight - 88, rect.bottom + 10)}px`;
        mobileNote.hidden = false;
    }

    function showPrimaryMenu() {
        popup.innerHTML = '<button type="button" data-copy>复制</button><button type="button" data-highlight>划线</button><button type="button" data-comment>评论</button>';
        popup.querySelector('[data-copy]').addEventListener('click', () => {
            copySelection(selected.selectedText).then(showCopyResult);
        });
        popup.querySelector('[data-highlight]').addEventListener('click', showHighlightMenu);
        popup.querySelector('[data-comment]').addEventListener('click', () => {
            openComposer(selected);
            hidePopup();
        });
    }

    function showHighlightMenu() {
        popup.innerHTML = '<button type="button" data-back>返回</button><button type="button" data-color="yellow" aria-label="琥珀色波浪线"></button><button type="button" data-color="red" aria-label="珊瑚色直线"></button><button type="button" data-color="green" aria-label="绿色虚线"></button><button type="button" data-color="blue" aria-label="蓝色双线"></button><button type="button" data-cancel>取消</button>';
        popup.querySelector('[data-back]').addEventListener('click', showPrimaryMenu);
        popup.querySelector('[data-cancel]').addEventListener('click', hidePopup);
        popup.querySelectorAll('[data-color]').forEach(button => {
            button.addEventListener('click', () => {
                color = button.dataset.color;
                createHighlight();
            });
        });
    }

    function buildPopup() {
        popup = document.createElement('div');
        popup.className = 'bd-annotation-popup';
        document.body.appendChild(popup);
        showPrimaryMenu();
    }

    function showPopup(range) {
        if (!popup) {
            buildPopup();
        }
        selected = selectionData(range);
        showPrimaryMenu();
        popup.classList.add('bd-annotation-popup-open');
        const rect = range.getBoundingClientRect();
        popup.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - popup.offsetWidth - 8))}px`;
        popup.style.top = `${Math.max(8, rect.bottom + 9)}px`;
    }

    function showAnnotationActions(annotation, mark) {
        if (!popup) {
            buildPopup();
        }
        popup.innerHTML = '<button type="button" data-delete-annotation>删除划线</button>';
        popup.querySelector('[data-delete-annotation]').addEventListener('click', () => {
            hidePopup();
            removeAnnotation(annotation.id, () => {
                if (!popup) {
                    buildPopup();
                }
                popup.classList.add('bd-annotation-popup-open');
                const rect = mark.getBoundingClientRect();
                popup.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - popup.offsetWidth - 8))}px`;
                popup.style.top = `${Math.max(8, rect.bottom + 9)}px`;
                popup.replaceChildren(Object.assign(document.createElement('span'), {
                    className: 'bd-annotation-copy-result',
                    textContent: '删除失败，请重试'
                }));
                window.setTimeout(() => {
                    if (popup.contains(popup.querySelector('.bd-annotation-copy-result'))) {
                        hidePopup();
                    }
                }, 1600);
            });
        });
        popup.classList.add('bd-annotation-popup-open');
        const rect = mark.getBoundingClientRect();
        popup.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - popup.offsetWidth - 8))}px`;
        popup.style.top = `${Math.max(8, rect.bottom + 9)}px`;
    }

    function createHighlight() {
        if (!selected) {
            return;
        }
        const highlighted = selected;
        api('', 'POST', {
            selectedText: selected.selectedText.slice(0, 500),
            annotationText: null,
            color,
            visibility: 'PRIVATE',
            startOffset: selected.startOffset,
            endOffset: selected.endOffset
        }).then(saved => {
            annotations.push(saved);
            renderMarks();
            restoreSelection(highlighted);
            updateCommentCount();
            if (isOpen()) {
                renderFeed();
            }
            hidePopup();
        }).catch(() => {
            if (!popup) {
                return;
            }
            popup.replaceChildren(Object.assign(document.createElement('span'), {
                className: 'bd-annotation-copy-result',
                textContent: '划线失败，请重试'
            }));
            window.setTimeout(() => {
                if (popup.contains(popup.querySelector('.bd-annotation-copy-result'))) {
                    hidePopup();
                }
            }, 1600);
        });
    }

    toggle.addEventListener('click', () => setOpen(!isOpen()));
    sidebar.querySelector('.bd-annotation-sidebar-close').addEventListener('click', () => setOpen(false));
    sidebar.querySelector('.bd-annotation-composer-cancel').addEventListener('click', closeComposer);
    sidebar.querySelector('.bd-annotation-composer-save').addEventListener('click', saveComposer);
    sidebar.querySelectorAll('[data-bd-annotation-color]').forEach(button => {
        button.addEventListener('click', () => selectColor(button.dataset.bdAnnotationColor));
    });
    composer.querySelector('.bd-annotation-visibility').addEventListener('change', () => {
        visibilityChanged = true;
    });
    composer.querySelector('.bd-annotation-composer-text').addEventListener('input', event => {
        if (!visibilityChanged) {
            composer.querySelector('.bd-annotation-visibility').value = event.target.value.trim() ? 'PUBLIC' : 'PRIVATE';
        }
    });

    document.addEventListener('mouseup', event => {
        if (isMobile() || sidebar.contains(event.target) || popup && popup.contains(event.target)) {
            return;
        }
        const selection = window.getSelection();
        if (!selection.rangeCount) {
            return;
        }
        const range = selection.getRangeAt(0);
        if (range.collapsed || !range.toString().trim()
            || !isContentNode(range.startContainer) || !isContentNode(range.endContainer)) {
            return;
        }
        showPopup(range);
    });

    content.addEventListener('click', event => {
        const mark = event.target.closest && event.target.closest('mark.bd-annotation-highlight');
        if (!mark) {
            return;
        }
        const annotation = annotations.find(item => String(item.id) === mark.dataset.id);
        if (isMobile()) {
            if (annotation) {
                showMobileComment(annotation, mark);
            }
            return;
        }
        if (annotation && annotation.ownedByCurrentVisitor && !hasComment(annotation)) {
            showAnnotationActions(annotation, mark);
        }
    });

    document.addEventListener('click', event => {
        if (!popup || !popup.classList.contains('bd-annotation-popup-open')
            || eventOccurredInside(event, popup) || eventOccurredInside(event, sidebar)
            || event.target.closest('mark.bd-annotation-highlight') || hasActiveContentSelection()) {
            return;
        }
        hidePopup();
    });

    // 在正文重新按下鼠标时先关闭旧菜单并清空旧选区：单击不会遗留菜单，
    // 拖拽产生的新选区仍会在 mouseup 时正常打开菜单。
    document.addEventListener('mousedown', event => {
        if (!popup || !popup.classList.contains('bd-annotation-popup-open')
            || eventOccurredInside(event, popup) || eventOccurredInside(event, sidebar)) {
            return;
        }
        hidePopup();
        window.getSelection().removeAllRanges();
    }, true);

    document.addEventListener('click', event => {
        if (mobileNote && !mobileNote.hidden && !mobileNote.contains(event.target) && !event.target.closest('mark.bd-annotation-highlight')) {
            mobileNote.hidden = true;
        }
    });
    window.addEventListener('scroll', () => {
        renderCommentOutlines();
        if (isOpen()) {
            requestAnimationFrame(layoutFeed);
        }
    }, {passive: true});
    window.addEventListener('resize', () => {
        renderCommentOutlines();
        if (isOpen()) {
            renderFeed();
        }
    });

    renderMarks();
    updateCommentCount();
    setOpen(readSidebarState() && !isMobile());
    article.dataset.bdAnnotationReady = 'true';
})();
