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

    @Test
    void escapesRawHtmlAndUnsafeLinkProtocols() {
        String rendered = renderer.render("<img src=x onerror=alert(1)>\n\n[危险链接](javascript:alert(1))");

        assertThat(rendered)
                .contains("&lt;img")
                .doesNotContain("<img src=x onerror=alert(1)>")
                .doesNotContain("onerror=")
                .doesNotContain("javascript:");
    }

    @Test
    void preservesStandardMarkdownStructureAfterSanitizing() {
        String rendered = renderer.render("# 标题\n\n```java\nint value = 1;\n```\n\n| 名称 | 值 |\n| --- | --- |\n| A | 1 |");

        assertThat(rendered)
                .contains("<h1>标题</h1>")
                .contains("<pre><code")
                .contains("int value &#61; 1;")
                .contains("<table>");
    }

    @Test
    void preservesRestrictedLanguageClassForFencedMermaidCode() {
        String rendered = renderer.render("```mermaid\ngraph TD\n  A --> B\n```");

        assertThat(rendered)
                .contains("<pre><code class=\"language-mermaid\">")
                .contains("graph TD");
    }

    @Test
    void preservesValidatedImageWidthFromMarkdownTitle() {
        String rendered = renderer.render("![架构图](/images/diagram.png \"width=700\")");

        assertThat(rendered)
                .contains("<img src=\"/images/diagram.png\" alt=\"架构图\" width=\"700\" />")
                .doesNotContain("title=");
    }

    @Test
    void rendersStandardMarkdownImageWithoutATitle() {
        String rendered = renderer.render("![历史图片](/images/cache.png)");

        assertThat(rendered)
                .contains("<img src=\"/images/cache.png\" alt=\"历史图片\" />")
                .doesNotContain("width=")
                .doesNotContain("title=");
    }

    @Test
    void rendersLegacyMarkdownImageWithANonWidthTitle() {
        String rendered = renderer.render("![历史图片](/images/cache.png \"缓存架构图\")");

        assertThat(rendered)
                .contains("<img src=\"/images/cache.png\" alt=\"历史图片\" />")
                .doesNotContain("width=")
                .doesNotContain("title=");
    }

    @Test
    void ignoresImageWidthOutsideTheAcceptedRange() {
        String rendered = renderer.render("![架构图](/images/diagram.png \"width=99\")");

        assertThat(rendered)
                .contains("<img src=\"/images/diagram.png\" alt=\"架构图\" />")
                .doesNotContain("width=");
    }
}
