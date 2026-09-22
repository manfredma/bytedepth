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
                .contains("overscroll-behavior-y: none")
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
    void rssDiscoveryIsHiddenOnlyInStaging() throws Exception {
        String nav = classpathText("/templates/fragments/nav.html");
        String pwaHead = classpathText("/templates/fragments/pwa-head.html");

        assertThat(nav)
                .contains("th:if=\"${environment != 'staging'}\"")
                .contains("th:href=\"@{/feed.xml}\"");
        assertThat(pwaHead)
                .contains("th:if=\"${environment != 'staging'}\"")
                .contains("th:if=\"${environment == 'staging'}\" name=\"robots\"")
                .contains("noindex, nofollow, noarchive");
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
                .contains(".nav-bar[data-env=\"staging\"]")
                .contains("--bd-nav-bg: #2d1b4e")
                .contains(".nav-bar[data-env=\"staging\"]::before")
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
    void visualizationLibrariesArePinnedToLocalStaticAssets() throws Exception {
        String analytics = classpathText("/templates/admin/analytics.html");
        String postDetail = classpathText("/templates/public/posts/detail.html");

        assertThat(analytics)
                .contains("@{/vendor/echarts/echarts.min.js}")
                .doesNotContain("cdn.jsdelivr.net/npm/echarts");
        assertThat(postDetail)
                .contains("@{/vendor/mermaid/mermaid.min.js}\" defer")
                .doesNotContain("cdn.jsdelivr.net/npm/mermaid")
                .contains("if (typeof mermaid === 'undefined')");
        assertThat(getClass().getResource("/static/vendor/echarts/echarts.min.js")).isNotNull();
        assertThat(getClass().getResource("/static/vendor/mermaid/mermaid.min.js")).isNotNull();
    }

    @Test
    void publicPostDetailShowsContentVersionMetadata() throws Exception {
        String template = classpathText("/templates/public/posts/detail.html");

        assertThat(template)
                .contains("版本 v")
                .contains("${post.contentVersion}");
    }

    @Test
    void serviceWorkerUsesVersionedCacheFirstStaticAssets() throws Exception {
        String sw = classpathText("/static/sw.js");

        assertThat(sw)
                .contains("bytedepth-v8")
                .contains("/favicon.ico")
                .contains("/icons/favicon-48.png")
                .contains("/favicon-staging.ico")
                .contains("/icons/favicon-staging-48.png")
                .contains("/icons/favicon-staging-192.png")
                .contains("/icons/favicon-staging-512.png")
                .contains("内容指纹 URL cache-first")
                .contains("if (request.method !== 'GET') return;")
                .contains("if (cached) return cached")
                .doesNotContain("admin-layout.css")
                .doesNotContain("isFreshStaticAsset");
    }

    @Test
    void siteIconUsesOneHighContrastAssetFamilyForSearchAndPwa() throws Exception {
        String pwaHead = classpathText("/templates/fragments/pwa-head.html");
        String faviconFragment = classpathText("/templates/fragments/favicon-head.html");
        String manifest = classpathText("/static/manifest.json");

        assertThat(pwaHead)
                .contains("fragments/favicon-head :: favicon")
                .doesNotContain("/icons/logo.svg");
        assertThat(faviconFragment)
                .contains("/icons/favicon-48.png")
                .contains("/icons/favicon-192.png")
                .contains("/favicon.ico");
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
    void stagingUsesASeparateAmberFaviconFamilyWithoutChangingTheProductionIcon() throws Exception {
        String faviconFragment = classpathText("/templates/fragments/favicon-head.html");
        String pwaHead = classpathText("/templates/fragments/pwa-head.html");
        String stagingManifest = classpathText("/static/manifest-staging.json");
        String stagingSvg = classpathText("/static/icons/favicon-staging.svg");

        assertThat(faviconFragment)
                .contains("environment == 'staging'")
                .contains("/icons/favicon-staging.svg")
                .contains("/icons/favicon-staging-48.png")
                .contains("/icons/favicon-staging-192.png")
                .contains("/favicon-staging.ico")
                .contains("/icons/favicon.svg")
                .contains("/icons/favicon-48.png")
                .contains("/icons/favicon-192.png")
                .contains("/favicon.ico");
        assertThat(pwaHead)
                .contains("/manifest-staging.json")
                .contains("/manifest.json");
        assertThat(stagingManifest)
                .contains("bytedepth staging")
                .contains("/icons/favicon-staging.svg")
                .contains("/icons/favicon-staging-192.png")
                .contains("/icons/favicon-staging-512.png");
        assertThat(stagingSvg)
                .contains("fill=\"#78350f\"")
                .contains("stroke=\"#fbbf24\"")
                .contains(">B</text>");

        assertThat(getClass().getResource("/static/favicon-staging.ico")).isNotNull();
        assertThat(getClass().getResource("/static/icons/favicon-staging-48.png")).isNotNull();
        assertThat(getClass().getResource("/static/icons/favicon-staging-192.png")).isNotNull();
        assertThat(getClass().getResource("/static/icons/favicon-staging-512.png")).isNotNull();
    }

    @Test
    void cssAssetsUseContentHashVersioningInsteadOfManualVersions() throws Exception {
        String config = classpathText("/application.yml");

        assertThat(config)
                .contains("chain:\n        enabled: true")
                .contains("content:\n            enabled: true\n            paths:\n              - /css/**\n              - /js/**");
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
    void navbarProvidesNetworkNavigationAndStagingProductionNotice() throws Exception {
        String navTemplate = classpathText("/templates/fragments/nav.html");

        assertThat(navTemplate)
                .contains("th:href=\"@{/network}\"")
                .contains("th:if=\"${environment == 'staging'}\"")
                .contains("https://bytedepth.cn")
                .contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void networkMapUsesTheSharedPublicPageBackgroundAndMarginReset() throws Exception {
        String networkMap = classpathText("/templates/public/network.html");

        assertThat(networkMap)
                .contains("body { font-family: var(--serif); margin: 0; background: var(--bd-bg, #f0f2f5);");
    }

    @Test
    void adminLayoutUsesAVisibleStagingPaletteOnTheEnvironmentBearingShell() throws Exception {
        String css = classpathText("/static/css/admin-layout.css");
        String theme = classpathText("/static/css/theme.css");
        String sidebar = classpathText("/templates/fragments/admin-sidebar.html");
        String dashboard = classpathText("/templates/admin/dashboard.html");
        String postEditor = classpathText("/static/css/post-editor.css");
        String analytics = classpathText("/templates/admin/analytics.html");

        assertThat(sidebar).contains("data-env=${environment}");
        assertThat(css)
                .contains(".admin-shell:has(.admin-sidebar[data-env=\"staging\"])")
                .contains(".admin-sidebar[data-env=\"staging\"]")
                .contains("--admin-accent: #7c3aed")
                .contains(".aq-primary")
                .contains("background: var(--admin-accent)")
                .contains(".admin-stat-card .stat-num")
                .contains("color: var(--admin-accent)")
                .contains(".admin-sidebar[data-env=\"staging\"]::before")
                .contains("content: \"staging\"");
        assertThat(dashboard).doesNotContain("#e94560");
        assertThat(postEditor)
                .contains("color: var(--admin-accent)")
                .contains("background: var(--admin-accent)");
        assertThat(analytics)
                .contains("function adminAccent()")
                .contains("color: adminAccent()")
                .doesNotContain("#e94560");
        assertThat(theme).doesNotContain(".nav-bar[data-env=\"staging\"]");
    }

    @Test
    void publicHeadDeclaresRssAutodiscovery() throws Exception {
        assertThat(classpathText("/templates/fragments/pwa-head.html"))
                .contains("rel=\"alternate\" type=\"application/rss+xml\"")
                .contains("href=\"/feed.xml\"");
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
                .contains("class=\"nav-about-menu\"")
                .contains("<a th:href=\"@{/about}\">关于本站</a>")
                .contains("<a th:href=\"@{/releases}\">版本</a>")
                .doesNotContain("<a th:href=\"@{/releases}\">版本</a>\n            </div>")
                .contains("nav-theme-placeholder")
                .contains("action=\"/search\"")
                .contains("method=\"get\"")
                .doesNotContain("<style>")
                .doesNotContain(".nav-bar {");
    }
}
