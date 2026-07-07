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
                .contains("class=\"nav-inner\"")
                .contains("max-width:1060px")
                .contains("background:var(--bd-nav-bg")
                .contains("class=\"nav-left\"")
                .contains("class=\"nav-primary\"")
                .contains("class=\"nav-actions\"")
                .contains("class=\"nav-about\"")
                .contains("action=\"/search\"")
                .contains("method=\"get\"");
    }
}
