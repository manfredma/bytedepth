package manfred.bytedepth.adapter.web.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchHighlightTest {

    @Test
    void rendersOnlySearchEmphasisAsHtmlAndRemovesMarkdown() {
        String rendered = SearchHighlight.snippet("> **高性能** 的 <em>位运算</em> 技巧。<script>alert(1)</script>");

        assertThat(rendered)
                .contains("高性能 的 <em>位运算</em> 技巧。")
                .doesNotContain("**")
                .doesNotContain("script");
    }
}
