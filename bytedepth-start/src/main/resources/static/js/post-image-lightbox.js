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
    const close = () => dialog.close();
    dialog.querySelector('.bd-image-lightbox__close').addEventListener('click', close);
    dialog.addEventListener('click', event => {
        if (event.target === dialog) {
            close();
        }
    });

    const isSvgImage = image => {
        const source = image.currentSrc || image.src;
        return source.startsWith('data:image/svg+xml') || new URL(source, window.location.href).pathname.toLowerCase().endsWith('.svg');
    };
    const open = image => {
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
