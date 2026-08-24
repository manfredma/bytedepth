(() => {
    'use strict';
    const content = document.querySelector('#post-article .content');
    if (!content) {
        return;
    }

    const dialog = document.createElement('dialog');
    dialog.className = 'bd-image-lightbox';
    dialog.setAttribute('aria-label', '图片预览');
    dialog.innerHTML = '<div class="bd-image-lightbox__frame"><img class="bd-image-lightbox__image" alt=""><button class="bd-image-lightbox__close" type="button" aria-label="关闭图片预览">×</button></div>';
    document.body.appendChild(dialog);
    const frame = dialog.querySelector('.bd-image-lightbox__frame');
    const preview = dialog.querySelector('.bd-image-lightbox__image');
    const MIN_SCALE = 1;
    const MAX_SCALE = 4;
    let scale = MIN_SCALE;
    let panX = 0;
    let panY = 0;
    let pinchStartDistance = 0;
    let pinchStartScale = MIN_SCALE;
    let gestureStartScale = MIN_SCALE;
    let gestureActive = false;
    let pointerStartX = 0;
    let pointerStartY = 0;
    let pointerStartPanX = 0;
    let pointerStartPanY = 0;
    let pointerActive = false;

    const applyTransform = () => {
        preview.style.transform = `translate(${panX}px, ${panY}px) scale(${Number(scale.toFixed(3))})`;
    };
    const applyScale = requestedScale => {
        scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, requestedScale));
        applyTransform();
    };
    const resetZoom = () => {
        pinchStartDistance = 0;
        gestureActive = false;
        pointerActive = false;
        panX = 0;
        panY = 0;
        applyScale(MIN_SCALE);
    };
    const touchDistance = touches => Math.hypot(
        touches[1].clientX - touches[0].clientX,
        touches[1].clientY - touches[0].clientY
    );
    const close = () => {
        resetZoom();
        dialog.close();
    };

    dialog.querySelector('.bd-image-lightbox__close').addEventListener('click', close);
    dialog.addEventListener('close', resetZoom);
    dialog.addEventListener('click', event => {
        if (event.target === dialog) {
            close();
        }
    });
    // Chromium 将触控板 pinch 表示为 ctrl+wheel；passive:false 才能阻止页面视口缩放。
    dialog.addEventListener('wheel', event => {
        if (!dialog.open || !event.ctrlKey) {
            return;
        }
        event.preventDefault();
        applyScale(scale * Math.exp(-event.deltaY * 0.01));
    }, { passive: false });
    // 移动端浏览器使用 Touch Events；只在双指手势期间接管，单指操作不受影响。
    dialog.addEventListener('touchstart', event => {
        if (!dialog.open || event.touches.length !== 2) {
            return;
        }
        event.preventDefault();
        pinchStartDistance = touchDistance(event.touches);
        pinchStartScale = scale;
    }, { passive: false });
    dialog.addEventListener('touchmove', event => {
        if (!dialog.open || event.touches.length !== 2 || !pinchStartDistance) {
            return;
        }
        event.preventDefault();
        if (!gestureActive) {
            applyScale(pinchStartScale * touchDistance(event.touches) / pinchStartDistance);
        }
    }, { passive: false });
    dialog.addEventListener('touchend', event => {
        if (event.touches.length < 2) {
            pinchStartDistance = 0;
        }
    });
    // Safari 的触控板和部分触屏设备使用专有 Gesture Events，并可能同时派发 touchmove。
    dialog.addEventListener('gesturestart', event => {
        if (!dialog.open) {
            return;
        }
        event.preventDefault();
        gestureActive = true;
        gestureStartScale = scale;
    }, { passive: false });
    dialog.addEventListener('gesturechange', event => {
        if (!dialog.open || !gestureActive) {
            return;
        }
        event.preventDefault();
        applyScale(gestureStartScale * event.scale);
    }, { passive: false });
    dialog.addEventListener('gestureend', event => {
        if (!dialog.open || !gestureActive) {
            return;
        }
        event.preventDefault();
        gestureActive = false;
    }, { passive: false });
    // 放大后（scale > 1）允许在灯箱内拖动平移查看溢出部分。
    // 用 Pointer Events 统一鼠标与单指触摸；双指缩放由上面 Touch/Gesture 事件处理，
    // 这里只在非双指手势期间接管单指/鼠标拖动。
    preview.addEventListener('pointerdown', event => {
        if (!dialog.open || scale <= MIN_SCALE || (event.pointerType === 'touch' && event.isPrimary === false)) {
            return;
        }
        pointerStartX = event.clientX;
        pointerStartY = event.clientY;
        pointerStartPanX = panX;
        pointerStartPanY = panY;
        pointerActive = true;
        if (typeof preview.setPointerCapture === 'function') {
            preview.setPointerCapture(event.pointerId);
        }
    });
    window.addEventListener('pointermove', event => {
        if (!pointerActive) {
            return;
        }
        event.preventDefault();
        panX = pointerStartPanX + (event.clientX - pointerStartX);
        panY = pointerStartPanY + (event.clientY - pointerStartY);
        applyTransform();
    }, { passive: false });
    window.addEventListener('pointerup', () => {
        pointerActive = false;
    });
    window.addEventListener('pointercancel', () => {
        pointerActive = false;
    });

    const isSvgImage = image => {
        const source = image.currentSrc || image.src;
        return source.startsWith('data:image/svg+xml') || new URL(source, window.location.href).pathname.toLowerCase().endsWith('.svg');
    };
    const open = image => {
        resetZoom();
        preview.src = image.currentSrc || image.src;
        preview.alt = image.alt || '';
        // SVG 默认透明；只在预览画布补白，保持文章内原图和其他图片不受影响。
        frame.classList.toggle('bd-image-lightbox__frame--svg', isSvgImage(image));
        dialog.showModal();
    };
    content.querySelectorAll('img').forEach(image => {
        image.dataset.bdLightbox = '';
        image.tabIndex = 0;
        image.setAttribute('role', 'button');
        image.setAttribute('aria-label', `${image.alt || '图片'}，点击放大`);
        image.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            open(image);
        });
        image.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                event.stopPropagation();
                open(image);
            }
        });
    });
})();
