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

        assertThat(template)
                .contains(".series-trigger.open {")
                .contains("transform: translateX(-100%) translateY(-50%)")
                .contains("pointer-events: none")
                .contains("trigger.classList.toggle('open')");
    }

    private String classpathText(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("classpath resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
