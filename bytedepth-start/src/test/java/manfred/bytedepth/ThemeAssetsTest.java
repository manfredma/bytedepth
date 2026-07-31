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
                .contains("scrollbar-gutter: stable")
                .contains("html[data-theme=\"paper\"]")
                .contains("html[data-theme=\"blue\"]")
                .contains("html[data-theme=\"green\"]")
                .contains("html[data-theme=\"midnight\"]")
                .contains("html[data-theme=\"rose\"]")
                .contains(".theme-switcher")
                .contains("--bd-page-max: 1060px")
                .contains("--bd-page-pad: 20px");

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
    void navStaticAssetDefinesIsolatedComponentContract() throws Exception {
        String css = classpathText("/static/css/nav.css");

        assertThat(css)
                .contains("@import url('https://fonts.googleapis.com")
                .contains(".nav-bar")
                .contains("font-family:var(--bd-font-sans")
                .contains("font-size:16px")
                .contains(".nav-bar *,")
                .contains(".nav-bar *::before,")
                .contains(".nav-bar *::after")
                .contains("box-sizing:border-box")
                .contains("max-width:var(--bd-page-max, 1060px)")
                .contains("padding:0 var(--bd-page-pad, 20px)")
                .contains("background:var(--bd-nav-bg")
                .contains(".nav-theme-placeholder")
                .contains("visibility:hidden")
                .contains("flex:0 0 36px")
                .doesNotContain("font-size:1.34em");
    }

    @Test
    void adminLayoutDefinesMobileFloatingSidebarContract() throws Exception {
        String css = classpathText("/static/css/admin-layout.css");
        String sidebar = classpathText("/templates/fragments/admin-sidebar.html");

        assertThat(css)
                .contains("@media (max-width: 768px)")
                .contains(".admin-sidebar-toggle")
                .contains("bottom: calc(14px + env(safe-area-inset-bottom))")
                .contains(".admin-sidebar-overlay.open")
                .contains(".admin-sidebar.open")
                .contains("position: fixed")
                .contains("transform: translateX(0)")
                .contains(".admin-main table")
                .contains("overflow-x: auto");

        assertThat(sidebar)
                .contains("id=\"adminSidebarToggle\"")
                .contains("id=\"adminSidebarOverlay\"")
                .contains("id=\"adminSidebar\"")
                .contains("aria-expanded")
                .contains("admin-menu-open")
                .contains("setOpen(false)");
    }

    @Test
    void adminTemplatesDeclareMobileViewport() throws Exception {
        List<String> templates = List.of(
                "/templates/admin/analytics.html",
                "/templates/admin/categories/list.html",
                "/templates/admin/comments/list.html",
                "/templates/admin/dashboard.html",
                "/templates/admin/posts/edit.html",
                "/templates/admin/posts/list.html",
                "/templates/admin/projects/edit.html",
                "/templates/admin/series/detail.html",
                "/templates/admin/series/list.html",
                "/templates/admin/tags/list.html",
                "/templates/admin/users/list.html",
                "/templates/admin/view-logs/list.html"
        );

        for (String template : templates) {
            String html = classpathText(template);
            assertThat(html).as(template)
                    .contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">")
                    .contains("@{/css/admin-layout.css}");
        }
    }

    @Test
    void postEditorUsesScopedVditorAssetsAndSynchronizesMarkdownBeforeSaving() throws Exception {
        String template = classpathText("/templates/admin/posts/edit.html");
        String css = classpathText("/static/css/post-editor.css");
        String js = classpathText("/static/js/post-editor.js");

        assertThat(template)
                .contains("@{/css/post-editor.css}")
                .contains("vditor@3.11.2")
                .contains("name=\"_csrf\"")
                .contains("name=\"_csrf_header\"")
                .contains("id=\"content-editor\"")
                .contains("id=\"vditor-editor\"");
        assertThat(css)
                .contains(".post-editor-page")
                .contains(".post-editor-form")
                .contains("@media (max-width: 980px)")
                .doesNotContain("body {")
                .doesNotContain("\nhtml {")
                .doesNotContain("\n* {");
        assertThat(js)
                .contains("new Vditor")
                .contains("source.value = editor.getValue()")
                .contains("/admin/images/upload")
                .contains("csrfHeaders")
                .contains("headers: csrfHeaders()");
    }

    @Test
    void serviceWorkerUsesVersionedCacheFirstStaticAssets() throws Exception {
        String sw = classpathText("/static/sw.js");

        assertThat(sw)
                .contains("bytedepth-v4")
                .contains("/favicon.ico")
                .contains("/icons/favicon-48.png")
                .contains("静态资源：cache-first")
                .contains("if (cached) return cached")
                .doesNotContain("admin-layout.css")
                .doesNotContain("isFreshStaticAsset");
    }

    @Test
    void siteIconUsesOneHighContrastAssetFamilyForSearchAndPwa() throws Exception {
        String pwaHead = classpathText("/templates/fragments/pwa-head.html");
        String manifest = classpathText("/static/manifest.json");

        assertThat(pwaHead)
                .contains("/icons/favicon-48.png")
                .contains("/icons/favicon-192.png")
                .contains("/favicon.ico")
                .doesNotContain("/icons/logo.svg");
        assertThat(manifest)
                .contains("/icons/favicon.svg")
                .contains("/icons/favicon-192.png")
                .contains("/icons/favicon-512.png")
                .doesNotContain("/icons/logo.svg");

        assertThat(getClass().getResource("/static/favicon.ico")).isNotNull();
        assertThat(getClass().getResource("/static/icons/favicon-48.png")).isNotNull();
        assertThat(getClass().getResource("/static/icons/favicon-192.png")).isNotNull();
        assertThat(getClass().getResource("/static/icons/favicon-512.png")).isNotNull();
    }

    @Test
    void cssAssetsUseContentHashVersioningInsteadOfManualVersions() throws Exception {
        String config = classpathText("/application.yml");

        assertThat(config)
                .contains("chain:\n        enabled: true")
                .contains("content:\n            enabled: true\n            paths: /css/**");
    }

    @Test
    void redisSessionNamespaceIsVersionedToRejectIncompatibleLegacySessions() throws Exception {
        String config = classpathText("/application.yml");

        assertThat(config)
                .contains("namespace: bytedepth:session:v2")
                .doesNotContain("namespace: bytedepth:session\n");
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
                "/templates/public/profile.html",
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
    void publicShellTemplatesUseColumnTypographyBaseline() throws Exception {
        String css = classpathText("/static/css/theme.css");
        assertThat(css)
                .contains("--bd-font-serif")
                .contains("--bd-font-display")
                .contains("--bd-font-sans");

        List<String> templates = List.of(
                "/templates/public/index.html",
                "/templates/public/posts/list.html",
                "/templates/public/columns/list.html",
                "/templates/public/columns/detail.html",
                "/templates/public/search.html",
                "/templates/public/about.html",
                "/templates/public/projects/list.html",
                "/templates/public/profile.html",
                "/templates/public/login.html",
                "/templates/public/register.html"
        );

        for (String template : templates) {
            String html = classpathText(template);
            assertThat(html).as(template).contains("font-family: var(--serif)");
            assertThat(html).as(template).doesNotContain("-apple-system");
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

    @Test
    void navbarUsesBoundedHeaderLayout() throws Exception {
        String nav = classpathText("/templates/fragments/nav.html");

        assertThat(nav)
                .contains("@{/css/nav.css}")
                .contains("class=\"nav-inner\"")
                .contains("class=\"nav-left\"")
                .contains("class=\"nav-primary\"")
                .contains("class=\"nav-actions\"")
                .contains("class=\"nav-about\"")
                .contains("nav-theme-placeholder")
                .contains("action=\"/search\"")
                .contains("method=\"get\"")
                .doesNotContain("<style>")
                .doesNotContain(".nav-bar {");
    }
}
