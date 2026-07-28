package manfred.bytedepth.adapter.web.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void countsVisibleUnicodeCharactersWithoutMarkdownSyntaxOrWhitespace() {
        assertThat(renderer.countVisibleCharacters("# 标题\n[链接](https://example.com) `代码` 😀"))
                .isEqualTo(7);
    }

    @Test
    void returnsZeroForBlankContent() {
        assertThat(renderer.countVisibleCharacters(" \n\t")).isZero();
    }
}
