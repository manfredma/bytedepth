(function () {
    var storageKey = 'bytedepth.theme';
    var allowedThemes = ['default', 'paper', 'blue', 'green', 'midnight', 'rose'];

    function isAllowed(theme) {
        return allowedThemes.indexOf(theme) !== -1;
    }

    function readStoredTheme() {
        try {
            var theme = window.localStorage.getItem(storageKey);
            return isAllowed(theme) ? theme : 'default';
        } catch (e) {
            return 'default';
        }
    }

    function persistTheme(theme) {
        try {
            if (theme === 'default') {
                window.localStorage.removeItem(storageKey);
            } else {
                window.localStorage.setItem(storageKey, theme);
            }
        } catch (e) {
            // Keep the visual change even when storage is unavailable.
        }
    }

    function updateMenu(theme) {
        var options = document.querySelectorAll('[data-theme-option]');
        for (var i = 0; i < options.length; i++) {
            var option = options[i];
            var active = option.getAttribute('data-theme-option') === theme;
            option.classList.toggle('active', active);
            option.setAttribute('aria-pressed', active ? 'true' : 'false');
        }
    }

    function applyTheme(theme) {
        var nextTheme = isAllowed(theme) ? theme : 'default';
        if (nextTheme === 'default') {
            document.documentElement.removeAttribute('data-theme');
        } else {
            document.documentElement.setAttribute('data-theme', nextTheme);
        }
        updateMenu(nextTheme);
    }

    function closeMenus(except) {
        var switchers = document.querySelectorAll('.theme-switcher.open');
        for (var i = 0; i < switchers.length; i++) {
            if (switchers[i] !== except) {
                switchers[i].classList.remove('open');
                var trigger = switchers[i].querySelector('.theme-trigger');
                if (trigger) {
                    trigger.setAttribute('aria-expanded', 'false');
                }
            }
        }
    }

    function initThemeSwitcher() {
        applyTheme(readStoredTheme());

        var switchers = document.querySelectorAll('.theme-switcher');
        for (var i = 0; i < switchers.length; i++) {
            (function (switcher) {
                var trigger = switcher.querySelector('.theme-trigger');
                var options = switcher.querySelectorAll('[data-theme-option]');

                if (trigger) {
                    trigger.addEventListener('click', function (event) {
                        event.stopPropagation();
                        var willOpen = !switcher.classList.contains('open');
                        closeMenus(switcher);
                        switcher.classList.toggle('open', willOpen);
                        trigger.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
                    });
                }

                for (var j = 0; j < options.length; j++) {
                    options[j].addEventListener('click', function () {
                        var theme = this.getAttribute('data-theme-option');
                        applyTheme(theme);
                        persistTheme(theme);
                        switcher.classList.remove('open');
                        if (trigger) {
                            trigger.setAttribute('aria-expanded', 'false');
                        }
                    });
                }
            })(switchers[i]);
        }

        document.addEventListener('click', function () {
            closeMenus(null);
        });
    }

    applyTheme(readStoredTheme());

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initThemeSwitcher);
    } else {
        initThemeSwitcher();
    }
})();
