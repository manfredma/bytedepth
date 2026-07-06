# Frontend Theme Switcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the current bytedepth dark brand theme as the default while adding five selectable frontend themes persisted in browser `localStorage`.

**Architecture:** Add a small CSS variable theme layer plus a vanilla JavaScript switcher. Wire the switcher into the shared public navigation, then map public Thymeleaf template colors to the new `--bd-*` variables without changing page structure. Backend code is not required.

**Tech Stack:** Spring Boot 3, Thymeleaf templates, static CSS/JavaScript, JUnit 5 resource/template assertions, Maven with Java 21.

---

## File Structure

- Create `bytedepth-start/src/main/resources/static/css/theme.css`: shared theme variables, theme menu styles, and public page utility tokens.
- Create `bytedepth-start/src/main/resources/static/js/theme-switcher.js`: localStorage persistence, early theme application, menu initialization, and selection state updates.
- Create `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`: verifies static assets and key templates contain theme integration.
- Modify `bytedepth-start/src/main/resources/templates/fragments/nav.html`: load theme assets, add compact theme menu, convert nav colors to variables.
- Modify `bytedepth-start/src/main/resources/templates/fragments/pagination.html`: convert pagination jump controls to variables.
- Modify public templates: `index.html`, `posts/list.html`, `posts/detail.html`, `columns/list.html`, `columns/detail.html`, `search.html`, `about.html`, `projects/list.html`, `login.html`, `register.html`.
- Do not modify `bytedepth-start/src/main/resources/templates/admin/**` or `bytedepth-start/src/main/resources/static/css/admin-layout.css`.

---

### Task 1: Add Theme Asset Tests

**Files:**
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`

- [ ] **Step 1: Write the failing test**

Create `ThemeAssetsTest.java` with resource and template assertions:

```java
package manfred.bytedepth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeAssetsTest {

    private String classpathText(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("classpath resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void themeStaticAssetsExistAndDefineExpectedContracts() throws Exception {
        String css = classpathText("/static/css/theme.css");
        String js = classpathText("/static/js/theme-switcher.js");

        assertThat(css)
                .contains("--bd-bg")
                .contains("html[data-theme=\"paper\"]")
                .contains("html[data-theme=\"blue\"]")
                .contains("html[data-theme=\"green\"]")
                .contains("html[data-theme=\"midnight\"]")
                .contains("html[data-theme=\"rose\"]")
                .contains(".theme-switcher");

        assertThat(js)
                .contains("bytedepth.theme")
                .contains("paper")
                .contains("blue")
                .contains("green")
                .contains("midnight")
                .contains("rose")
                .contains("data-theme");
    }

    @Test
    void publicTemplatesLoadThemeAssets() throws Exception {
        List<String> templates = List.of(
                "/templates/public/index.html",
                "/templates/public/posts/list.html",
                "/templates/public/posts/detail.html",
                "/templates/public/columns/list.html",
                "/templates/public/columns/detail.html",
                "/templates/public/search.html",
                "/templates/public/about.html",
                "/templates/public/projects/list.html",
                "/templates/public/login.html",
                "/templates/public/register.html"
        );

        for (String template : templates) {
            String html = classpathText(template);
            assertThat(html).as(template).contains("@{/css/theme.css}");
            assertThat(html).as(template).contains("@{/js/theme-switcher.js}");
        }
    }

    @Test
    void navbarContainsThemeSwitcherMarkup() throws Exception {
        String nav = classpathText("/templates/fragments/nav.html");

        assertThat(nav)
                .contains("theme-switcher")
                .contains("data-theme-option=\"default\"")
                .contains("data-theme-option=\"paper\"")
                .contains("data-theme-option=\"blue\"")
                .contains("data-theme-option=\"green\"")
                .contains("data-theme-option=\"midnight\"")
                .contains("data-theme-option=\"rose\"");
    }
}
```

- [ ] **Step 2: Run the new test to verify it fails**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -Dtest=ThemeAssetsTest test -Dsort.skip=true
```

Expected: FAIL because `theme.css`, `theme-switcher.js`, and theme markup do not exist yet.

- [ ] **Step 3: Commit the failing test**

Do not commit yet if the team avoids red commits. If committing red tests is acceptable, use:

```bash
git add bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java
git commit -m "test: add theme asset contract tests"
```

---

### Task 2: Add Static Theme CSS and JavaScript

**Files:**
- Create: `bytedepth-start/src/main/resources/static/css/theme.css`
- Create: `bytedepth-start/src/main/resources/static/js/theme-switcher.js`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`

- [ ] **Step 1: Create `theme.css`**

Add the CSS variable layer and theme menu styles:

```css
:root {
    --bd-bg: #f0f2f5;
    --bd-surface: #ffffff;
    --bd-surface-muted: #f4f6f8;
    --bd-text: #1a1a2e;
    --bd-text-muted: #555555;
    --bd-text-subtle: #999999;
    --bd-border: #dddddd;
    --bd-accent: #e94560;
    --bd-accent-hover: #c73652;
    --bd-nav-bg: #1a1a2e;
    --bd-nav-text: #eeeeee;
    --bd-code-bg: #0d1117;
    --bd-code-text: #e6edf3;
    --bd-highlight-bg: #fff5f7;
    --bd-success: #2ecc71;
    --bd-danger: #c00;
    --bd-shadow: 0 2px 8px rgba(0,0,0,.08);
    --bd-shadow-strong: 0 8px 32px rgba(0,0,0,.3);
}

html[data-theme="paper"] {
    --bd-bg: #f7f5f0;
    --bd-surface: #fffaf2;
    --bd-surface-muted: #f0e9dc;
    --bd-text: #1f1d1a;
    --bd-text-muted: #5f574d;
    --bd-text-subtle: #9a8f80;
    --bd-border: #ded4c3;
    --bd-accent: #b86b4b;
    --bd-accent-hover: #98543a;
    --bd-nav-bg: #2b2a27;
    --bd-nav-text: #f6efe4;
    --bd-code-bg: #202124;
    --bd-code-text: #f3eadc;
    --bd-highlight-bg: #fff1e8;
    --bd-shadow: 0 2px 10px rgba(88,70,46,.12);
}

html[data-theme="blue"] {
    --bd-bg: #f5f7fa;
    --bd-surface: #ffffff;
    --bd-surface-muted: #eaf2fb;
    --bd-text: #172033;
    --bd-text-muted: #526071;
    --bd-text-subtle: #8b9aaa;
    --bd-border: #d8e2ed;
    --bd-accent: #2f80ed;
    --bd-accent-hover: #1f66c2;
    --bd-nav-bg: #10233f;
    --bd-nav-text: #eef6ff;
    --bd-code-bg: #0b1726;
    --bd-code-text: #dbeafe;
    --bd-highlight-bg: #eaf3ff;
}

html[data-theme="green"] {
    --bd-bg: #f2f7f1;
    --bd-surface: #ffffff;
    --bd-surface-muted: #e7f0e5;
    --bd-text: #17251b;
    --bd-text-muted: #526154;
    --bd-text-subtle: #8a9a8c;
    --bd-border: #d4e0d1;
    --bd-accent: #2f855a;
    --bd-accent-hover: #276749;
    --bd-nav-bg: #14311f;
    --bd-nav-text: #edf7ef;
    --bd-code-bg: #111b14;
    --bd-code-text: #d8f3dc;
    --bd-highlight-bg: #e9f7ec;
}

html[data-theme="midnight"] {
    --bd-bg: #111827;
    --bd-surface: #172033;
    --bd-surface-muted: #202b3f;
    --bd-text: #e5e7eb;
    --bd-text-muted: #cbd5e1;
    --bd-text-subtle: #94a3b8;
    --bd-border: #334155;
    --bd-accent: #38bdf8;
    --bd-accent-hover: #0ea5e9;
    --bd-nav-bg: #0b1120;
    --bd-nav-text: #e5e7eb;
    --bd-code-bg: #020617;
    --bd-code-text: #e2e8f0;
    --bd-highlight-bg: #0f2a3a;
    --bd-shadow: 0 2px 12px rgba(0,0,0,.35);
}

html[data-theme="rose"] {
    --bd-bg: #fff5f6;
    --bd-surface: #ffffff;
    --bd-surface-muted: #ffe8ec;
    --bd-text: #30171d;
    --bd-text-muted: #68434c;
    --bd-text-subtle: #a77b84;
    --bd-border: #f2c9d1;
    --bd-accent: #d94662;
    --bd-accent-hover: #be3450;
    --bd-nav-bg: #3b1823;
    --bd-nav-text: #fff1f4;
    --bd-code-bg: #241116;
    --bd-code-text: #ffe4e9;
    --bd-highlight-bg: #ffe8ec;
}

.theme-switcher {
    position: relative;
}

.theme-trigger {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border: 1px solid rgba(255,255,255,.18);
    border-radius: 6px;
    background: rgba(255,255,255,.08);
    color: var(--bd-nav-text);
    cursor: pointer;
    font-size: 1rem;
    line-height: 1;
}

.theme-trigger:hover,
.theme-trigger[aria-expanded="true"] {
    border-color: var(--bd-accent);
    color: var(--bd-accent);
}

.theme-menu {
    position: absolute;
    right: 0;
    top: calc(100% + 8px);
    display: none;
    min-width: 168px;
    padding: 8px;
    border: 1px solid var(--bd-border);
    border-radius: 8px;
    background: var(--bd-surface);
    box-shadow: var(--bd-shadow);
    z-index: 1001;
}

.theme-switcher.open .theme-menu {
    display: grid;
    gap: 4px;
}

.theme-option {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 7px 8px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--bd-text-muted);
    cursor: pointer;
    font-size: .88rem;
    text-align: left;
}

.theme-option:hover,
.theme-option.active {
    background: var(--bd-surface-muted);
    color: var(--bd-accent);
}

.theme-swatch {
    width: 16px;
    height: 16px;
    border-radius: 50%;
    border: 1px solid var(--bd-border);
    flex-shrink: 0;
}

.theme-swatch.default { background: linear-gradient(135deg, #1a1a2e 50%, #e94560 50%); }
.theme-swatch.paper { background: linear-gradient(135deg, #f7f5f0 50%, #b86b4b 50%); }
.theme-swatch.blue { background: linear-gradient(135deg, #f5f7fa 50%, #2f80ed 50%); }
.theme-swatch.green { background: linear-gradient(135deg, #f2f7f1 50%, #2f855a 50%); }
.theme-swatch.midnight { background: linear-gradient(135deg, #111827 50%, #38bdf8 50%); }
.theme-swatch.rose { background: linear-gradient(135deg, #fff5f6 50%, #d94662 50%); }
```

- [ ] **Step 2: Create `theme-switcher.js`**

Add the JavaScript:

```javascript
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

    function applyTheme(theme) {
        var nextTheme = isAllowed(theme) ? theme : 'default';
        if (nextTheme === 'default') {
            document.documentElement.removeAttribute('data-theme');
        } else {
            document.documentElement.setAttribute('data-theme', nextTheme);
        }
        updateMenu(nextTheme);
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
```

- [ ] **Step 3: Run asset test**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -Dtest=ThemeAssetsTest test -Dsort.skip=true
```

Expected: still FAIL because templates and nav are not wired yet.

- [ ] **Step 4: Commit assets when test reaches the expected partial failure**

```bash
git add bytedepth-start/src/main/resources/static/css/theme.css bytedepth-start/src/main/resources/static/js/theme-switcher.js
git commit -m "feat: add frontend theme assets"
```

---

### Task 3: Wire Navigation and Pagination

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/fragments/nav.html`
- Modify: `bytedepth-start/src/main/resources/templates/fragments/pagination.html`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`

- [ ] **Step 1: Modify nav styles and add theme menu**

In `fragments/nav.html`, convert nav colors to variables and add this block inside `.nav-right`, before `.nav-auth`:

```html
<div class="theme-switcher">
    <button type="button" class="theme-trigger" aria-label="切换主题" aria-expanded="false">◐</button>
    <div class="theme-menu" role="menu">
        <button type="button" class="theme-option" data-theme-option="default" aria-pressed="true">
            <span class="theme-swatch default"></span><span>默认</span>
        </button>
        <button type="button" class="theme-option" data-theme-option="paper" aria-pressed="false">
            <span class="theme-swatch paper"></span><span>纸张阅读</span>
        </button>
        <button type="button" class="theme-option" data-theme-option="blue" aria-pressed="false">
            <span class="theme-swatch blue"></span><span>清爽蓝</span>
        </button>
        <button type="button" class="theme-option" data-theme-option="green" aria-pressed="false">
            <span class="theme-swatch green"></span><span>森林绿</span>
        </button>
        <button type="button" class="theme-option" data-theme-option="midnight" aria-pressed="false">
            <span class="theme-swatch midnight"></span><span>深夜代码</span>
        </button>
        <button type="button" class="theme-option" data-theme-option="rose" aria-pressed="false">
            <span class="theme-swatch rose"></span><span>玫瑰暖色</span>
        </button>
    </div>
</div>
```

Use these replacements in the nav `<style>`:

```css
.nav-bar { position:sticky; top:0; z-index:1000; background:var(--bd-nav-bg); color:var(--bd-nav-text); }
.nav-brand span { color:var(--bd-accent); font-weight:bold; font-size:1.1em; letter-spacing:1px; }
.nav-links a { color:var(--bd-nav-text); text-decoration:none; font-size:0.95em; white-space:nowrap; }
.nav-links a:hover { color:var(--bd-accent); }
.nav-search input { padding:5px 10px; border:none; border-radius:4px 0 0 4px; font-size:0.85em; outline:none; width:150px; background:var(--bd-surface); color:var(--bd-text); }
.nav-search button { padding:5px 10px; background:var(--bd-accent); color:white; border:none; border-radius:0 4px 4px 0; cursor:pointer; line-height:1; }
.nav-auth a, .nav-auth button { color:rgba(255,255,255,.72); text-decoration:none; font-size:0.88em; background:none; border:none; cursor:pointer; }
.nav-auth .nav-register { color:var(--bd-accent); font-weight:600; }
.nav-auth .nav-admin { color:var(--bd-accent); font-weight:600; }
```

- [ ] **Step 2: Modify pagination variables**

In `fragments/pagination.html`, replace hard-coded colors in the inline style:

```css
.page-jump { display: inline-flex; align-items: center; gap: 5px;
             font-size: .85em; color: var(--bd-text-subtle); margin-left: 10px; vertical-align: middle; }
.jump-input { width: 52px; padding: 5px 7px; border: 1px solid var(--bd-border); border-radius: 4px;
              font-size: .85em; text-align: center; outline: none;
              -moz-appearance: textfield; background: var(--bd-surface); color: var(--bd-text); }
.jump-input:focus { border-color: var(--bd-accent); }
.jump-btn { padding: 5px 10px; background: var(--bd-accent); color: white;
            border: none; border-radius: 4px; cursor: pointer;
            font-size: .85em; transition: background .15s; line-height: 1.4; }
.jump-btn:hover { background: var(--bd-accent-hover); }
```

- [ ] **Step 3: Run nav markup test**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -Dtest=ThemeAssetsTest#navbarContainsThemeSwitcherMarkup test -Dsort.skip=true
```

Expected: PASS.

- [ ] **Step 4: Commit nav and pagination**

```bash
git add bytedepth-start/src/main/resources/templates/fragments/nav.html bytedepth-start/src/main/resources/templates/fragments/pagination.html
git commit -m "feat: add theme switcher to public nav"
```

---

### Task 4: Wire Public Template Asset Includes

**Files:**
- Modify public templates listed in the spec.
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`

- [ ] **Step 1: Add asset includes to every public template**

Add these lines inside `<head>` after the PWA head block or favicon:

```html
<link rel="stylesheet" th:href="@{/css/theme.css}">
<script th:src="@{/js/theme-switcher.js}"></script>
```

Apply to:

```text
bytedepth-start/src/main/resources/templates/public/index.html
bytedepth-start/src/main/resources/templates/public/posts/list.html
bytedepth-start/src/main/resources/templates/public/posts/detail.html
bytedepth-start/src/main/resources/templates/public/columns/list.html
bytedepth-start/src/main/resources/templates/public/columns/detail.html
bytedepth-start/src/main/resources/templates/public/search.html
bytedepth-start/src/main/resources/templates/public/about.html
bytedepth-start/src/main/resources/templates/public/projects/list.html
bytedepth-start/src/main/resources/templates/public/login.html
bytedepth-start/src/main/resources/templates/public/register.html
```

- [ ] **Step 2: Run template include test**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -Dtest=ThemeAssetsTest#publicTemplatesLoadThemeAssets test -Dsort.skip=true
```

Expected: PASS.

- [ ] **Step 3: Commit includes**

```bash
git add bytedepth-start/src/main/resources/templates/public
git commit -m "feat: load theme assets on public pages"
```

---

### Task 5: Convert Common Public Page Colors to Theme Variables

**Files:**
- Modify: `public/index.html`, `public/posts/list.html`, `public/search.html`, `public/about.html`, `public/projects/list.html`, `public/login.html`, `public/register.html`

- [ ] **Step 1: Convert simple page colors**

Use these replacements where equivalent hard-coded colors exist:

```css
body { background: var(--bd-bg); color: var(--bd-text); }
background: white;      /* replace with */ background: var(--bd-surface);
background: #f0f2f5;    /* replace with */ background: var(--bd-bg);
background: #f4f6f8;    /* replace with */ background: var(--bd-surface-muted);
color: #1a1a2e;         /* replace with */ color: var(--bd-text);
color: #444;            /* replace with */ color: var(--bd-text-muted);
color: #555;            /* replace with */ color: var(--bd-text-muted);
color: #888;            /* replace with */ color: var(--bd-text-subtle);
color: #999;            /* replace with */ color: var(--bd-text-subtle);
color: #aaa;            /* replace with */ color: var(--bd-text-subtle);
border-color: #ddd;     /* replace with */ border-color: var(--bd-border);
border: 1px solid #ddd; /* replace with */ border: 1px solid var(--bd-border);
background: #e94560;    /* replace with */ background: var(--bd-accent);
color: #e94560;         /* replace with */ color: var(--bd-accent);
background: #c73652;    /* replace with */ background: var(--bd-accent-hover);
box-shadow: 0 2px 8px rgba(0,0,0,.08); /* replace with */ box-shadow: var(--bd-shadow);
```

Keep semantic colors where they communicate status, such as login success green and error red, unless they use brand red for decoration.

- [ ] **Step 2: Ensure default visual parity**

For the default theme, `--bd-*` values match the existing black/red palette. Confirm that replacing colors with variables does not change default layout or spacing.

- [ ] **Step 3: Commit simple page conversion**

```bash
git add bytedepth-start/src/main/resources/templates/public/index.html \
        bytedepth-start/src/main/resources/templates/public/posts/list.html \
        bytedepth-start/src/main/resources/templates/public/search.html \
        bytedepth-start/src/main/resources/templates/public/about.html \
        bytedepth-start/src/main/resources/templates/public/projects/list.html \
        bytedepth-start/src/main/resources/templates/public/login.html \
        bytedepth-start/src/main/resources/templates/public/register.html
git commit -m "feat: theme common public pages"
```

---

### Task 6: Convert Reading and Column Pages

**Files:**
- Modify: `public/posts/detail.html`, `public/columns/list.html`, `public/columns/detail.html`

- [ ] **Step 1: Map local reading variables to shared variables**

In pages that already define `:root` local variables, replace color values with shared variables:

```css
:root {
    --bg: var(--bd-bg);
    --card: var(--bd-surface);
    --ink: var(--bd-text);
    --ink-2: var(--bd-text-muted);
    --ink-3: var(--bd-text-subtle);
    --accent: var(--bd-accent);
    --accent-dk: var(--bd-accent-hover);
    --navy: var(--bd-nav-bg);
    --border: var(--bd-border);
    --code-bg: var(--bd-code-bg);
    --code-fg: var(--bd-code-text);
}
```

Keep typography variables such as `--serif`, `--display`, `--sans`, `--mono`, `--radius`, and `--shadow`, but set `--shadow: var(--bd-shadow);`.

- [ ] **Step 2: Convert remaining hard-coded decorative colors**

Replace inline reading colors where they should follow the theme:

```css
.content code { background: var(--bd-surface-muted); color: var(--bd-accent-hover); border: 1px solid var(--bd-border); }
.content blockquote { background: var(--bd-highlight-bg); }
.content tr:nth-child(even) { background: var(--bd-surface-muted); }
```

Keep warning/status badge colors in `posts/detail.html` unchanged if they represent publishing status.

- [ ] **Step 3: Commit reading page conversion**

```bash
git add bytedepth-start/src/main/resources/templates/public/posts/detail.html \
        bytedepth-start/src/main/resources/templates/public/columns/list.html \
        bytedepth-start/src/main/resources/templates/public/columns/detail.html
git commit -m "feat: theme reading pages"
```

---

### Task 7: Verification

**Files:**
- All touched files.

- [ ] **Step 1: Run focused test**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -Dtest=ThemeAssetsTest test -Dsort.skip=true
```

Expected: PASS.

- [ ] **Step 2: Refresh local Maven cache**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
```

Expected: build success.

- [ ] **Step 3: Run full tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true
```

Expected: all tests pass.

- [ ] **Step 4: Manual browser verification**

Start the app:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start spring-boot:run -Dsort.skip=true
```

Verify in the browser:

```text
http://localhost:8080/
http://localhost:8080/posts
http://localhost:8080/search
http://localhost:8080/about
http://localhost:8080/projects
http://localhost:8080/login
http://localhost:8080/register
```

Expected:

- Default theme remains the current black/red brand look.
- Theme menu appears in public pages with the shared nav.
- Selecting each theme changes colors immediately.
- Refresh preserves the selected theme.
- Selecting default restores the original theme and removes `bytedepth.theme` from `localStorage`.
- Login/register apply saved themes but show no theme menu.
- Admin pages keep their existing admin layout.

- [ ] **Step 5: Final commit**

If previous task commits were skipped, commit all implementation changes:

```bash
git add bytedepth-start/src/main/resources/static/css/theme.css \
        bytedepth-start/src/main/resources/static/js/theme-switcher.js \
        bytedepth-start/src/main/resources/templates/fragments/nav.html \
        bytedepth-start/src/main/resources/templates/fragments/pagination.html \
        bytedepth-start/src/main/resources/templates/public \
        bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java
git commit -m "feat: add public theme switcher"
```

---

## Self-Review

- Spec coverage: covers default theme preservation, five optional themes, localStorage persistence, public-only scope, login/register behavior, no backend persistence, and Maven verification.
- Placeholder scan: no TBD/TODO/fill-later placeholders remain.
- Type consistency: theme keys are consistently `default`, `paper`, `blue`, `green`, `midnight`, `rose`; storage key is consistently `bytedepth.theme`; CSS variable prefix is consistently `--bd-*`.
