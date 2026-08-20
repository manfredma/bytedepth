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
    void seriesTriggerMovesOutOfThePanelContentWhenThePanelIsOpen() throws IOException {
        String template = classpathText("/templates/public/posts/detail.html");
        String navigationScript = classpathText("/static/js/series-navigation.js");

        assertThat(template)
                .contains(".series-trigger.open {")
                .contains("transform: translateX(-100%) translateY(-50%)")
                .contains("pointer-events: none")
                .contains("background: rgba(0,0,0,.18)")
                .doesNotContain(".series-overlay { display: none; position: fixed; inset: 0; z-index: 199; background: rgba(0,0,0,.3); backdrop-filter")
                .contains("trigger.classList.toggle('open')")
                .contains("@{/js/series-navigation.js}")
                .contains("id=\"post-article\"")
                .contains("bd-annotation-reading-content")
                .doesNotContain("评论会贴近对应段落显示；仅你自己的私有划线对其他读者不可见。");
        assertThat(navigationScript)
                .contains("fetch(targetUrl.href")
                .contains("article.replaceWith(nextArticle)")
                .contains("panel.querySelectorAll('.series-item')")
                .contains("window.history.pushState");
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
