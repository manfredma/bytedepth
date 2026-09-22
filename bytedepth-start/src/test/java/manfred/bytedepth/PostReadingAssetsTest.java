package manfred.bytedepth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PostReadingAssetsTest {

    @Test
    void articlePageLoadsTheReadingTrackerAndUsesBeaconLifecycleEvents() throws IOException {
        String template = classpathText("/templates/public/posts/detail.html");
        String script = classpathText("/static/js/post-reading.js");

        assertThat(template)
                .contains("post-reading-tracker")
                .contains("/reading-progress")
                .contains("post-reading.js");
        assertThat(script)
                .contains("navigator.sendBeacon")
                .contains("visibilitychange")
                .contains("pagehide")
                .contains("activeReadSeconds")
                .contains("maxScrollDepth");
    }

    @Test
    void viewLogRendersTheCombinedReadingStatusColumn() throws IOException {
        String template = classpathText("/templates/admin/view-logs/list.html");

        assertThat(template)
                .contains("阅读情况")
                .contains("log.activeReadSeconds")
                .contains("log.maxScrollDepth")
                .contains("已完成")
                .contains("未完成");
    }

    @Test
    void seriesNavigationUsesAResponsiveLeftSidebar() throws IOException {
        String template = classpathText("/templates/public/posts/detail.html");

        assertThat(template)
                .contains("class=\"series-context\"")
                .contains("class=\"series-top-nav\"")
                .contains("aria-label=\"专栏上一篇下一篇\"")
                .contains("返回专栏")
                .contains("查看专栏目录")
                .contains("id=\"seriesPanel\"")
                .contains("id=\"seriesTrigger\"")
                .contains("打开专栏导航")
                .contains("aria-label=\"打开专栏导航\"")
                .contains("class=\"series-context-open\"")
                .contains("class=\"series-item\"")
                .contains("aria-current=${item.id == post.id ? 'page' : null}")
                .contains("class=\"series-post-nav\"")
                .contains("@{/columns/{slug}(slug=${series.slug})}")
                .contains("id=\"post-article\"")
                .contains("bd-annotation-reading-content")
                .doesNotContain("series-navigation.js")
                .doesNotContain("replaceArticle")
                .doesNotContain("评论会贴近对应段落显示；仅你自己的私有划线对其他读者不可见。");
    }

    @Test
    void seriesSidebarNameUsesTheSidebarContrastColor() throws IOException {
        String template = classpathText("/templates/public/posts/detail.html");

        assertThat(template)
                .contains("class=\"series-panel-name-link\"")
                .containsPattern("(?s)\\.series-panel-name-link\\s*\\{.*?color: var\\(--navy-fg\\);");
    }

    @Test
    void desktopSeriesNavigationStaysCompactWhileMobileKeepsTouchTarget() throws IOException {
        String template = classpathText("/templates/public/posts/detail.html");

        assertThat(template)
                .containsPattern("(?s)\\.series-post-nav\\s*\\{.*?min-height: 62px;")
                .containsPattern("(?s)\\.series-top-nav\\s*\\{.*?min-height: 62px;")
                .containsPattern("(?s)@media \\(max-width: 768px\\).*?\\.series-nav-link \\{ min-height: 76px;")
                .containsPattern("(?s)@media \\(max-width: 768px\\).*?\\.series-top-nav \\{.*?min-height: 76px;")
                .containsPattern("(?s)\\.series-panel\\s*\\{.*?position: fixed;")
                .containsPattern("(?s)@media \\(max-width: 768px\\).*?\\.series-panel \\{.*?width: min\\(86vw, 300px\\);")
                .containsPattern("(?s)\\.series-trigger\\s*\\{.*?top: 25%;.*?padding: 14px 9px;")
                .containsPattern("(?s)@media \\(max-width: 768px\\).*?\\.series-trigger\\s*\\{.*?padding: 8px 5px;.*?gap: 4px;")
                .doesNotContain("min-width: 54px;")
                .doesNotContain("min-height: 124px;")
                .doesNotContain("bottom: calc(78px + env(safe-area-inset-bottom));")
                .containsPattern("(?s)\\.series-context-open\\s*\\{.*?background: var\\(--accent\\);")
                .containsPattern("(?s)\\.series-panel-progress-bar\\s*\\{.*?height: 6px;")
                .contains("linear-gradient(90deg, #f1b44c, #e35a68)")
                .contains("class=\"series-panel-progress-marker\"")
                .contains("阅读进度 ·");
    }

    @Test
    void annotationComposerUsesAnUpwardSemanticTypePicker() throws IOException {
        String template = classpathText("/templates/public/posts/detail.html");
        String css = classpathText("/static/css/annotation.css");
        String script = classpathText("/static/js/annotation.js");

        assertThat(template)
                .contains("bd-annotation-composer-controls")
                .contains("data-bd-annotation-type=\"blue\"")
                .contains("data-bd-annotation-type=\"yellow\"")
                .contains("data-bd-annotation-type=\"green\"")
                .contains("data-bd-annotation-type=\"red\"")
                .doesNotContain("针对划线写评论");
        assertThat(css)
                .contains(".bd-annotation-type-menu")
                .contains("bottom: calc(100% + 7px)")
                .contains(".bd-annotation-feed-type")
                .contains(".bd-annotation-type-blue")
                .contains(".bd-annotation-type-yellow")
                .contains(".bd-annotation-type-green")
                .contains(".bd-annotation-type-red");
        assertThat(script)
                .contains("selectColor(existing ? existing.color : 'blue')")
                .contains("setTypeMenuOpen")
                .contains("annotationTypeLabel")
                .contains("data-bd-annotation-type");
    }

    private String classpathText(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("classpath resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
