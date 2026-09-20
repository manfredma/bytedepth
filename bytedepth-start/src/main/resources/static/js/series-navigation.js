(function () {
    'use strict';

    function updateActiveItem(panel, targetUrl) {
        panel.querySelectorAll('.series-item').forEach(function (item) {
            item.classList.toggle('active', new URL(item.href).pathname === targetUrl.pathname);
        });
    }

    function replaceArticle(responseText, targetUrl) {
        const nextDocument = new DOMParser().parseFromString(responseText, 'text/html');
        const nextArticle = nextDocument.getElementById('post-article');
        const article = document.getElementById('post-article');
        if (!nextArticle || !article) {throw new Error('未找到文章内容');}

        article.replaceWith(nextArticle);
        document.title = nextDocument.title;
        window.history.pushState({}, '', targetUrl.pathname + targetUrl.search + targetUrl.hash);

        const panel = document.getElementById('seriesPanel');
        if (panel) {updateActiveItem(panel, targetUrl);}
        window.scrollTo({ top: 0, behavior: 'smooth' });

        // 重新初始化批注：替换文章后旧 annotation.js 的 DOM 引用全部失效。
        reinitAnnotations(nextDocument);
    }

    function reinitAnnotations(nextDocument) {
        const inlineScripts = nextDocument.querySelectorAll('script:not([src])');
        inlineScripts.forEach(function (script) {
            if (script.textContent.indexOf('__ANNOTATIONS__') !== -1) {
                try {
                    const newScript = document.createElement('script');
                    newScript.textContent = script.textContent;
                    document.head.appendChild(newScript);
                    document.head.removeChild(newScript);
                } catch {
                    // 新文章批注数据初始化失败时不阻断文章切换。
                }
            }
        });

        if (typeof window.initAnnotations === 'function') {
            try {
                window.initAnnotations();
            } catch {
                // 重新初始化失败时不阻断文章切换。
            }
        }
    }

    function navigate(link) {
        const targetUrl = new URL(link.href, window.location.origin);
        const panel = document.getElementById('seriesPanel');
        if (!panel || targetUrl.origin !== window.location.origin) {return;}

        panel.setAttribute('aria-busy', 'true');
        fetch(targetUrl.href, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (response) {
                if (!response.ok) {throw new Error('加载文章失败');}
                return response.text();
            })
            .then(function (html) { replaceArticle(html, targetUrl); })
            .catch(function () { window.location.assign(targetUrl.href); })
            .finally(function () { panel.removeAttribute('aria-busy'); });
    }

    document.addEventListener('click', function (event) {
        const link = event.target.closest('.series-panel .series-item');
        if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {return;}
        event.preventDefault();
        navigate(link);
    });
}());
