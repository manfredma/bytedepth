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
                .contains("<h1 id=\"标题\">标题</h1>")
                .contains("<pre><code")
                .contains("int value &#61; 1;")
                .contains("<table>");
    }

    @Test
    void rendersAdjacentOrdinaryCodeBlocksWithTheUnifiedComponent() {
        String rendered = renderer.render("```java\nint value = 1;\n```\n\n```kotlin\nval value = 1\n```");

        assertThat(rendered)
                .contains("class=\"bd-code-block__language\">java</span>")
                .contains("class=\"bd-code-block__language\">kotlin</span>")
                .contains("class=\"bd-code-block__toggle\"")
                .contains("class=\"bd-code-block__copy\"")
                .contains("class=\"bd-code-block__lines\"")
                .doesNotContain("bd-code-tabs");
        assertThat(rendered.split("class=\"bd-code-block\"", -1)).hasSize(3);
    }

    @Test
    void rendersAnUnmarkedCodeBlockWithNeutralLabelWithoutInventingAFileName() {
        String rendered = renderer.render("```\nint value = 1;\n```");

        assertThat(rendered)
                .contains("class=\"bd-code-block__language\">Code</span>")
                .contains("class=\"bd-code-block__toggle\"")
                .contains("class=\"bd-code-block__copy\"")
                .contains("class=\"bd-code-block__line\">1</span>")
                .contains("int value &#61; 1;")
                .doesNotContain(".java")
                .doesNotContain("未标注语言");
    }

    @Test
    void rendersEmptyAndUnterminatedCodeBlocksWithCorrectLineCounts() {
        String empty = renderer.render("```java\n```");
        String unterminated = renderer.render("```java\nreturn 1;");

        assertThat(empty)
                .contains("class=\"bd-code-block__line\">1</span>");
        assertThat(unterminated)
                .contains("class=\"bd-code-block__line\">1</span>")
                .contains("return 1;");
    }

    @Test
    void rendersOptInCodeBlockTitleAndFoldControls() {
        String rendered = renderer.render("```java title:Example.java fold\nSystem.out.println(\"<safe>\");\n```");

        assertThat(rendered)
                .contains("class=\"bd-code-block bd-code-block--collapsed\"")
                .contains("Example.java")
                .contains("aria-expanded=\"false\"")
                .contains("hidden=\"hidden\"")
                .contains("System.out.println(&#34;&lt;safe&gt;&#34;);")
                .contains("class=\"bd-code-block__copy\"")
                .contains("class=\"bd-code-block__toggle\"");
    }

    @Test
    void rendersFoldOnlyBlockWithLanguageLabel() {
        String rendered = renderer.render("```java fold\nint value = 1;\n```");

        assertThat(rendered)
                .contains("class=\"bd-code-block__language\">java</span>")
                .contains("aria-label=\"展开代码\"")
                .contains("class=\"bd-code-block bd-code-block--collapsed\"");
    }

    @Test
    void omitsUnsafeLanguageClassForEnhancedCodeBlocks() {
        String rendered = renderer.render("```java<script> fold\nint value = 1;\n```");

        assertThat(rendered)
                .contains("class=\"bd-code-block bd-code-block--collapsed\"")
                .contains("&lt;script&gt;")
                .doesNotContain("class=\"language-java<script>\"");
    }

    @Test
    void groupsOnlyExplicitlyMarkedAdjacentCodeBlocks() {
        String rendered = renderer.render("```java title:Example.java tabs:install\njava code\n```\n\n```kotlin title:Example.kt tabs:install\nkotlin code\n```");

        assertThat(rendered)
                .contains("class=\"bd-code-tabs\"")
                .contains("bd-code-tabs__panel")
                .contains("role=\"tablist\"")
                .contains("Example.java")
                .contains("Example.kt")
                .contains("java code")
                .contains("kotlin code");
    }

    @Test
    void usesLanguageLabelsForTabsWithoutTitles() {
        String rendered = renderer.render("```java tabs:install\njava code\n```\n\n```kotlin tabs:install\nkotlin code\n```");

        assertThat(rendered)
                .contains("bd-code-tabs__tab");
        assertThat(rendered).containsPattern("bd-code-tabs__tab[^>]*>java</button>");
        assertThat(rendered).containsPattern("bd-code-tabs__tab[^>]*>kotlin</button>");
    }

    @Test
    void doesNotGroupExplicitTabBlocksAcrossParagraphsOrDifferentGroups() {
        String rendered = renderer.render("```java title:A.java tabs:first\na\n```\n\n说明\n\n```kotlin title:B.kt tabs:first\nb\n```\n\n```js title:C.js tabs:second\nc\n```");

        assertThat(rendered)
                .doesNotContain("class=\"bd-code-tabs\"")
                .contains("class=\"bd-code-block\"")
                .contains("说明");
    }

    @Test
    void leavesNestedCodeBlocksOnTheOrdinaryRenderingPath() {
        String rendered = renderer.render("> ```java title:Nested.java fold\n> nested\n> ```");

        assertThat(rendered)
                .contains("<pre><code class=\"language-java\">")
                .doesNotContain("bd-code-block")
                .doesNotContain("bd-code-tabs");
    }

    @Test
    void preservesObsidianInPageAnchorLinks() {
        String rendered = renderer.render("## TDD 三定律与工作流\n\n[跳转](#TDD%20三定律与工作流)");

        assertThat(rendered)
                .contains("<h2 id=\"TDD 三定律与工作流\">TDD 三定律与工作流</h2>")
                .contains("href=\"#TDD%20三定律与工作流\"");
    }

    @Test
    void preservesRestrictedLanguageClassForFencedMermaidCode() {
        String rendered = renderer.render("```mermaid\ngraph TD\n  A --> B\n```");

        assertThat(rendered)
                .contains("class=\"bd-code-block\"")
                .contains("<code class=\"language-mermaid\">")
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
