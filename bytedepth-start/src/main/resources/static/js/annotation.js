/* 阅读批注：匿名归属由 HttpOnly Cookie 在服务端维护。 */
window.initAnnotations = function () {
    'use strict';

    // 清理上一次初始化遗留的全局监听器，避免专栏切换文章后重复绑定
    if (window._bdAnnotationCleanup) { window._bdAnnotationCleanup(); }

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
    content.appendChild(commentOutlineLayer);
    const outlineNodes = new Map();

    let annotations = window.__ANNOTATIONS__ || [];
    let selected = null;
    let color = 'yellow';
    let visibilityChanged = false;
    let popup;
    let mobileNote;
    let mobileAnnotation;
    let mobileMark;
    // 当前在侧栏中聚焦展示的批注 id；用于判断点击同一批注 trigger 时是否应回收侧栏。
    let activeAnnotationId = null;
    let editingAnnotationId = null;
    // 评注展示方式：'follow'（卡片钉在划线高度，随正文滚动联动）/ 'compact'（铺开列表，不联动）。
    let feedLayout = 'follow';
    // 全部跟随元素共用一个帧句柄：同一滚动帧只读写一次，避免文字与批注产生布局反馈。
    let positionUpdateFrame = 0;

    let csrf = (document.querySelector('meta[name="_csrf"]') || {}).content || '';
    const composer = sidebar.querySelector('.bd-annotation-composer');
    const feed = sidebar.querySelector('.bd-annotation-feed');
    const sidebarCount = sidebar.querySelector('.bd-annotation-comment-count');
    const toolbarCount = toggle.querySelector('.bd-annotation-toolbar-count');
    const typePicker = composer.querySelector('.bd-annotation-type-picker');
    const typeTrigger = composer.querySelector('.bd-annotation-type-trigger');
    const typeLabel = composer.querySelector('.bd-annotation-type-label');
    const typeMenu = composer.querySelector('.bd-annotation-type-menu');

    function annotationTypeLabel(annotationColor) {
        return {blue: '补充说明', yellow: '重点摘录', green: '实践结论', red: '疑问待办'}[annotationColor] || '补充说明';
    }

    function isMobile() {
        return Boolean(window.matchMedia && window.matchMedia('(max-width: 768px)').matches);
    }

    function sendAnnotationRequest(path, method, payload) {
        return fetch(window.location.pathname + '/annotations' + path, {
            method,
            headers: {'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf},
            body: payload ? JSON.stringify(payload) : undefined
        });
    }

    // session 过期后旧 CSRF token 失效：重新拉取当前页面，从 meta 里取最新 token。
    // X-CSRF-Refresh 头让 Service Worker 放行，cache: 'no-store' 绕开 HTTP 缓存，确保拿到新 HTML。
    function refreshCsrfToken() {
        return fetch(window.location.pathname, {
            headers: {'X-Requested-With': 'XMLHttpRequest', 'X-CSRF-Refresh': '1'},
            cache: 'no-store'
        })
            .then(response => response.ok ? response.text() : Promise.reject(new Error('csrf refresh failed')))
            .then(html => {
                const meta = new DOMParser().parseFromString(html, 'text/html').querySelector('meta[name="_csrf"]');
                if (meta && meta.content) {
                    csrf = meta.content;
                }
            })
            .catch(() => { /* 刷新失败时保留现有 token，交由调用方按原状态码失败 */ });
    }

    function api(path, method, payload) {
        return sendAnnotationRequest(path, method, payload).then(response => {
            if (response.status === 403) {
                // CSRF token 失效（session 过期）：刷新一次 token 后重试，仍失败则按重试状态码报错。
                return refreshCsrfToken()
                    .then(() => sendAnnotationRequest(path, method, payload))
                    .then(retry => {
                        if (!retry.ok) {
                            const err = new Error('annotation request failed');
                            err.status = retry.status;
                            throw err;
                        }
                        return retry.status === 204 ? null : retry.json();
                    });
            }
            if (!response.ok) {
                const err = new Error('annotation request failed');
                err.status = response.status;
                throw err;
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
        if (!open) {
            activeAnnotationId = null;
        }
        // 侧栏开关会改变正文的 Grid 布局；下一帧后评注框必须按新坐标重绘。
        scheduleAnnotationPositionUpdate();
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
        outlineNodes.clear();
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

    function renderAnnotationText(container, annotation) {
        if (annotation.annotationHtml) {
            container.innerHTML = annotation.annotationHtml;
        } else {
            container.textContent = annotation.annotationText;
        }
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
            // 点击正文「评注」标签：聚焦/取消该批注，滚动正文到划线并选中；与点击卡片双向同步。
            focusAnnotation(annotation.id);
            // 聚焦后让对应卡片滚入侧栏可视区（跟随型下卡片已对齐划线，紧凑型下需主动滚入）。
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
        const contentRect = content.getBoundingClientRect();
        const contentTop = contentRect.top + window.scrollY;
        const activeKeys = new Set();
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
                const outlineKey = `${annotation.id}:${index}`;
                activeKeys.add(outlineKey);
                let outline = outlineNodes.get(outlineKey);
                if (!outline) {
                    outline = document.createElement('div');
                    outlineNodes.set(outlineKey, outline);
                    commentOutlineLayer.appendChild(outline);
                }
                outline.className = `bd-annotation-comment-outline bd-annotation-color-${annotation.color}`;
                if (String(activeAnnotationId) === String(annotation.id)) {
                    outline.classList.add('bd-annotation-comment-outline-active');
                }
                outline.dataset.annotationId = annotation.id;
                outline.dataset.textTop = String(row.top);
                // absolute 锚 .content：文档坐标（不随滚动变）一次算定，滚动时随容器整体带走。
                const top = `${row.top + window.scrollY - contentTop - labelGutter}px`;
                const left = `${row.left - contentRect.left}px`;
                if (outline.style.top !== top) {
                    outline.style.top = top;
                }
                if (outline.style.left !== left) {
                    outline.style.left = left;
                }
                const width = `${row.right - row.left}px`;
                const height = `${row.bottom - row.top + labelGutter}px`;
                if (outline.style.width !== width) {
                    outline.style.width = width;
                }
                if (outline.style.height !== height) {
                    outline.style.height = height;
                }
                if (index === 0 && !outline.firstChild) {
                    outline.appendChild(createCommentTrigger(annotation));
                }
            });
        });
        outlineNodes.forEach((outline, outlineKey) => {
            if (!activeKeys.has(outlineKey)) {
                outline.remove();
                outlineNodes.delete(outlineKey);
            }
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

    function layoutFeed(updateSpacer = false) {
        const items = Array.from(feed.querySelectorAll('.bd-annotation-feed-item'));
        const spacer = feed.querySelector('.bd-annotation-feed-spacer');

        // 紧凑型，或侧栏非 fixed（中屏侧栏在正文下方）时：常规流堆叠，清掉跟随定位。
        const followMode = feedLayout === 'follow'
            && getComputedStyle(sidebar).position === 'fixed';
        if (!followMode) {
            items.forEach(item => {
                item.hidden = false;
                item.style.transform = '';
                item.style.opacity = '';
            });
            if (spacer) {
                spacer.style.height = '';
            }
            return;
        }

        // 跟随型：每张卡片钉在侧栏内对应划线的高度，正文滚动时同步上下移动。
        const feedRect = feed.getBoundingClientRect();
        let nextTop = Number.NEGATIVE_INFINITY;
        items.forEach(item => {
            const mark = content.querySelector(`mark[data-id="${item.dataset.id}"]`);
            const markRect = mark && mark.getBoundingClientRect();
            // 找不到对应划线（划线被删除等）时退回常规流堆叠，避免卡片丢失。
            if (!markRect) {
                item.hidden = false;
                item.style.transform = '';
                item.style.opacity = '';
                return;
            }
            item.hidden = false;
            const anchorTop = markRect.top - feedRect.top + feed.scrollTop;
            const top = Math.max(anchorTop, nextTop);
            // 划线滚出视口上方时隐藏对应卡片；下方待滚入的卡片保持可见。
            if (markRect.bottom < 0) {
                item.hidden = true;
            }
            const transform = `translate3d(0, ${top}px, 0)`;
            if (item.style.transform !== transform) {
                item.style.transform = transform;
            }
            // 卡片不重叠：下一张至少从本张底部 + 间距起算，最小高度 94px 兜底。
            nextTop = top + Math.max(item.offsetHeight, 94) + 12;
            // 保留真实跟随坐标（可为负）。由 feed 的滚动裁切区在标题下沿遮住离场卡片，
            // 不再降低整张卡片透明度，避免内容发白。
            item.style.opacity = '';
        });
        // 占位高度只在重绘或尺寸变化时更新；滚动中改它会触发侧栏的滚动锚定，反过来抖动卡片。
        if (spacer && updateSpacer) {
            spacer.style.height = `${Math.max(nextTop, 0)}px`;
        }
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

            if (String(editingAnnotationId) === String(annotation.id)) {
                mountInlineComposer(item, annotation);
                feed.appendChild(item);
                return;
            }

            const quote = document.createElement('blockquote');
            quote.textContent = annotation.selectedText;
            item.appendChild(quote);

            const text = document.createElement('div');
            text.className = 'bd-annotation-feed-text';
            renderAnnotationText(text, annotation);
            item.appendChild(text);

            const footer = document.createElement('footer');
            footer.className = 'bd-annotation-feed-footer';

            const type = document.createElement('span');
            type.className = `bd-annotation-feed-type bd-annotation-type-${annotation.color}`;
            type.textContent = annotationTypeLabel(annotation.color);
            footer.appendChild(type);

            const meta = document.createElement('span');
            meta.className = 'bd-annotation-feed-meta';
            const visibilityText = annotation.visibility === 'PRIVATE' ? '仅自己可见' : '公开评论';
            meta.textContent = annotation.createdAt ? `${visibilityText} · ${annotation.createdAt}` : visibilityText;
            footer.appendChild(meta);

            if (annotation.ownedByCurrentVisitor) {
                const actions = document.createElement('div');
                actions.className = 'bd-annotation-feed-actions';
                const edit = document.createElement('button');
                edit.type = 'button';
                edit.textContent = '编辑';
                edit.addEventListener('click', () => openInlineComposer(annotation, item));
                const remove = document.createElement('button');
                remove.type = 'button';
                remove.textContent = '删除';
                remove.addEventListener('click', () => {
                    if (!window.confirm('确定删除这条评注吗？')) {
                        return;
                    }
                    removeAnnotation(annotation.id, (status) => {
                        remove.textContent = '删除失败，请重试';
                        remove.setAttribute('data-status', status);
                        window.setTimeout(() => { remove.textContent = '删除'; }, 2000);
                    });
                });
                actions.append(edit, remove);
                footer.appendChild(actions);
            }
            item.appendChild(footer);
            item.addEventListener('click', event => {
                // 点击编辑/删除按钮时不触发定位选中。
                if (event.target.closest('button')) {
                    return;
                }
                focusAnnotation(annotation.id);
            });
            feed.appendChild(item);
        });
        const spacer = document.createElement('div');
        spacer.className = 'bd-annotation-feed-spacer';
        spacer.setAttribute('aria-hidden', 'true');
        feed.appendChild(spacer);
        updateActiveItem();
        requestAnimationFrame(() => layoutFeed(true));
    }

    // 切换评注展示方式：follow=卡片钉划线高度随正文联动；compact=铺开列表不联动。
    function setFeedLayout(next) {
        if (next !== 'follow' && next !== 'compact') {
            return;
        }
        feedLayout = next;
        sidebar.classList.toggle('bd-annotation-feed-follow', next === 'follow');
        sidebar.classList.toggle('bd-annotation-feed-compact', next === 'compact');
        sidebar.querySelectorAll('[data-bd-layout]').forEach(button => {
            button.setAttribute('aria-pressed', String(button.dataset.bdLayout === next));
        });
        renderFeed();
    }

    // 点击评注卡片 / 正文评注标签时聚焦某条批注：滚动正文到对应划线、卡片与正文划线框变深色选中。
    // 再次聚焦同一条则取消选中。侧栏关闭时聚焦会先打开侧栏。
    function focusAnnotation(annotationId) {
        const same = activeAnnotationId === annotationId;
        activeAnnotationId = same ? null : annotationId;
        if (!isOpen()) {
            setOpen(true);
        }
        updateActiveItem();
        renderCommentOutlines();
        if (activeAnnotationId !== null) {
            const mark = content.querySelector(`mark[data-id="${annotationId}"]`);
            if (mark) {
                mark.scrollIntoView?.({block: 'center', behavior: 'smooth'});
            }
        }
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

    function selectColor(nextColor) {
        color = nextColor;
        typeLabel.textContent = annotationTypeLabel(color);
        typeTrigger.dataset.color = color;
        typeMenu.querySelectorAll('[data-bd-annotation-type]').forEach(button => {
            button.setAttribute('aria-selected', String(button.dataset.bdAnnotationType === color));
        });
    }

    function setTypeMenuOpen(open) {
        typeMenu.hidden = !open;
        typeTrigger.setAttribute('aria-expanded', String(open));
    }

    function openComposer(data, existing) {
        editingAnnotationId = null;
        sidebar.appendChild(composer);
        configureComposer(data, existing);
        composer.hidden = false;
        setOpen(true);
        requestAnimationFrame(() => composer.querySelector('.bd-annotation-composer-text').focus());
    }

    function openInlineComposer(annotation, item) {
        editingAnnotationId = annotation.id;
        configureComposer(annotation, annotation);
        mountInlineComposer(item, annotation);
        requestAnimationFrame(() => composer.querySelector('.bd-annotation-composer-text').focus());
    }

    function configureComposer(data, existing) {
        selected = data;
        composer.dataset.editId = existing ? existing.id : '';
        composer.querySelector('.bd-annotation-composer-quote').textContent = data.selectedText;
        composer.querySelector('.bd-annotation-composer-text').value = existing ? existing.annotationText || '' : '';
        composer.querySelector('.bd-annotation-visibility').value = existing ? existing.visibility : 'PRIVATE';
        visibilityChanged = Boolean(existing);
        selectColor(existing ? existing.color : 'blue');
        setTypeMenuOpen(false);
    }

    function mountInlineComposer(item) {
        item.classList.add('bd-annotation-feed-item-editing');
        item.replaceChildren(composer);
        composer.hidden = false;
        requestAnimationFrame(layoutFeed);
    }

    function closeComposer() {
        composer.hidden = true;
        sidebar.appendChild(composer);
        setTypeMenuOpen(false);
        selected = null;
        editingAnnotationId = null;
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
        }).catch(err => {
            const saveButton = composer.querySelector('.bd-annotation-composer-save');
            saveButton.textContent = '保存失败，请重试';
            saveButton.setAttribute('data-status', err && err.status ? String(err.status) : '0');
        });
    }

    function removeAnnotation(id, onFailure) {
        api(`/${id}`, 'DELETE').then(() => {
            annotations = annotations.filter(annotation => String(annotation.id) !== String(id));
            renderMarks();
            renderFeed();
        }).catch(err => {
            // 删除失败时不移除本地标记，避免与服务端不一致；向用户反馈。
            if (typeof onFailure === 'function') {
                onFailure(err && err.status ? String(err.status) : '0');
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
        renderAnnotationText(mobileNote, annotation);
        mobileAnnotation = annotation;
        mobileMark = mark;
        mobileNote.hidden = false;
        updateMobileCommentPosition();
    }

    function updateMobileCommentPosition() {
        if (!mobileNote || !mobileAnnotation || !mobileMark) {
            return;
        }
        const rect = mobileMark.getBoundingClientRect();
        if (rect.bottom <= 0 || rect.top >= window.innerHeight) {
            mobileNote.hidden = true;
            return;
        }
        mobileNote.hidden = false;
        const left = Math.max(12, Math.min(rect.left, window.innerWidth - 316));
        const top = Math.min(window.innerHeight - 88, rect.bottom + 10);
        const transform = `translate3d(${left}px, ${top}px, 0)`;
        if (mobileNote.style.transform !== transform) {
            mobileNote.style.transform = transform;
        }
    }

    function dismissMobileComment() {
        if (mobileNote) {
            mobileNote.hidden = true;
        }
        mobileAnnotation = null;
        mobileMark = null;
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
            removeAnnotation(annotation.id, (status) => {
                if (!popup) {
                    buildPopup();
                }
                popup.classList.add('bd-annotation-popup-open');
                const rect = mark.getBoundingClientRect();
                popup.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - popup.offsetWidth - 8))}px`;
                popup.style.top = `${Math.max(8, rect.bottom + 9)}px`;
                const result = document.createElement('span');
                result.className = 'bd-annotation-copy-result';
                result.textContent = '删除失败，请重试';
                result.setAttribute('data-status', status);
                popup.replaceChildren(result);
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
        }).catch(err => {
            if (!popup) {
                return;
            }
            const result = document.createElement('span');
            result.className = 'bd-annotation-copy-result';
            result.textContent = '划线失败，请重试';
            result.setAttribute('data-status', err && err.status ? String(err.status) : '0');
            popup.replaceChildren(result);
            window.setTimeout(() => {
                if (popup.contains(popup.querySelector('.bd-annotation-copy-result'))) {
                    hidePopup();
                }
            }, 1600);
        });
    }

    toggle.addEventListener('click', () => setOpen(!isOpen()));
    sidebar.querySelector('.bd-annotation-sidebar-close').addEventListener('click', () => setOpen(false));
    sidebar.querySelectorAll('[data-bd-layout]').forEach(button => {
        button.addEventListener('click', () => setFeedLayout(button.dataset.bdLayout));
    });
    sidebar.querySelector('.bd-annotation-composer-cancel').addEventListener('click', closeComposer);
    sidebar.querySelector('.bd-annotation-composer-save').addEventListener('click', saveComposer);
    typeTrigger.addEventListener('click', () => setTypeMenuOpen(typeMenu.hidden));
    typeMenu.querySelectorAll('[data-bd-annotation-type]').forEach(button => {
        button.addEventListener('click', () => {
            selectColor(button.dataset.bdAnnotationType);
            setTypeMenuOpen(false);
        });
    });
    composer.querySelector('.bd-annotation-visibility').addEventListener('change', () => {
        visibilityChanged = true;
    });
    composer.querySelector('.bd-annotation-composer-text').addEventListener('input', event => {
        if (!visibilityChanged) {
            composer.querySelector('.bd-annotation-visibility').value = event.target.value.trim() ? 'PUBLIC' : 'PRIVATE';
        }
    });

    function onDocMouseUp(event) {
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
    }
    document.addEventListener('mouseup', onDocMouseUp);

    document.addEventListener('click', event => {
        if (!typePicker.contains(event.target)) {
            setTypeMenuOpen(false);
        }
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

    function onDocClickHidePopup(event) {
        if (!popup || !popup.classList.contains('bd-annotation-popup-open')
            || eventOccurredInside(event, popup) || eventOccurredInside(event, sidebar)
            || event.target.closest('mark.bd-annotation-highlight') || hasActiveContentSelection()) {
            return;
        }
        hidePopup();
    }
    document.addEventListener('click', onDocClickHidePopup);

    // 在正文重新按下鼠标时先关闭旧菜单并清空旧选区：单击不会遗留菜单，
    // 拖拽产生的新选区仍会在 mouseup 时正常打开菜单。
    function onDocMouseDown(event) {
        if (!popup || !popup.classList.contains('bd-annotation-popup-open')
            || eventOccurredInside(event, popup) || eventOccurredInside(event, sidebar)) {
            return;
        }
        hidePopup();
        window.getSelection().removeAllRanges();
    }
    document.addEventListener('mousedown', onDocMouseDown, true);

    function onDocClickMobileNote(event) {
        if (mobileNote && !mobileNote.hidden && !mobileNote.contains(event.target) && !event.target.closest('mark.bd-annotation-highlight')) {
            dismissMobileComment();
        }
    }
    document.addEventListener('click', onDocClickMobileNote);

    function scheduleAnnotationPositionUpdate() {
        if (positionUpdateFrame) {
            return;
        }
        positionUpdateFrame = requestAnimationFrame(() => {
            positionUpdateFrame = 0;
            updateMobileCommentPosition();
            if (isOpen() && feedLayout === 'follow') {
                layoutFeed();
            }
        });
    }

    function onScroll() {
        scheduleAnnotationPositionUpdate();
    }
    window.addEventListener('scroll', onScroll, {passive: true});

    function onResize() {
        renderCommentOutlines();
        scheduleAnnotationPositionUpdate();
        if (isOpen()) {
            renderFeed();
        }
    }
    window.addEventListener('resize', onResize);

    // 清理函数：专栏切换文章重新初始化前移除全局监听器，避免重复绑定
    window._bdAnnotationCleanup = function () {
        document.removeEventListener('mouseup', onDocMouseUp);
        document.removeEventListener('click', onDocClickHidePopup);
        document.removeEventListener('mousedown', onDocMouseDown, true);
        document.removeEventListener('click', onDocClickMobileNote);
        window.removeEventListener('scroll', onScroll);
        window.removeEventListener('resize', onResize);
        if (positionUpdateFrame) {
            cancelAnimationFrame(positionUpdateFrame);
        }
    };

    renderMarks();
    updateCommentCount();
    // 默认跟随型布局：卡片钉在划线高度，随正文滚动联动。
    sidebar.classList.add('bd-annotation-feed-follow');
    sidebar.querySelectorAll('[data-bd-layout="follow"]').forEach(button => {
        button.setAttribute('aria-pressed', 'true');
    });
    // 侧栏默认关闭：阅读区首屏保持完整，用户主动点击工具栏「评论」后再展开。
    setOpen(false);
    article.dataset.bdAnnotationReady = 'true';
    // 字体加载完成后文字位置变化，重算一次框坐标（其余时机随容器滚动天然跟随）。
    if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(renderCommentOutlines);
    }
};

// 首次加载自动初始化
window.initAnnotations();
