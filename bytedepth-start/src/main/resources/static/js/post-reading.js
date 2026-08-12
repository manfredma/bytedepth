(function () {
    const tracker = document.getElementById('post-reading-tracker');
    const article = document.querySelector('.content');
    if (!tracker || !article) {return;}

    const activityWindowMs = 60_000;
    const reportIntervalMs = 15_000;
    let activeSeconds = 0;
    let maxScrollDepth = 0;
    let completed = false;
    let lastActivityAt = Date.now();

    function markActivity() { lastActivityAt = Date.now(); }
    ['scroll', 'touchstart', 'click', 'keydown'].forEach(type =>
        window.addEventListener(type, markActivity, {passive: true}));

    function updateDepth() {
        const rect = article.getBoundingClientRect();
        const viewportBottom = window.innerHeight;
        const articleHeight = Math.max(article.scrollHeight, rect.height);
        const visible = Math.max(0, Math.min(articleHeight, viewportBottom - rect.top));
        const depth = articleHeight <= window.innerHeight ? 100 : Math.round(visible * 100 / articleHeight);
        maxScrollDepth = Math.max(maxScrollDepth, Math.min(100, depth));
        if (maxScrollDepth >= 80 || (articleHeight <= window.innerHeight && activeSeconds >= 15)) {completed = true;}
    }

    function payload() {
        return JSON.stringify({
            visitToken: tracker.dataset.visitToken,
            activeReadSeconds: activeSeconds,
            maxScrollDepth: maxScrollDepth,
            completed: completed
        });
    }

    function report() {
        if (activeSeconds === 0) {return;}
        const body = new Blob([payload()], {type: 'application/json'});
        if (navigator.sendBeacon && navigator.sendBeacon(tracker.dataset.progressUrl, body)) {return;}
        fetch(tracker.dataset.progressUrl, {method: 'POST', body: payload(), headers: {'Content-Type': 'application/json'}, keepalive: true}).catch(() => {});
    }

    setInterval(() => {
        if (!document.hidden && Date.now() - lastActivityAt <= activityWindowMs) {
            activeSeconds++;
            updateDepth();
        }
    }, 1000);
    setInterval(report, reportIntervalMs);
    window.addEventListener('scroll', updateDepth, {passive: true});
    document.addEventListener('visibilitychange', () => { if (document.hidden) {report();} });
    window.addEventListener('pagehide', report);
    updateDepth();
})();
