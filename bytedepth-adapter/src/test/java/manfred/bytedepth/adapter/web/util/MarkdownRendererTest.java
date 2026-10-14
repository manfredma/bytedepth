package manfred.bytedepth.adapter.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void footnoteAnchorsRemainUniqueForNumericSuffixLabelsAndSafeForAnyLabel() {
        String html = renderer.render("正文[^n]，重复[^n]，另一个[^n-2]，特殊[^a<b]，长标签[^" + "x".repeat(257)
                + "]。\n\n[^n]: 第一条。\n\n[^n-2]: 第二条。\n\n[^a<b]: 第三条。\n\n[^" + "x".repeat(257) + "]: 第四条。");
        var matcher = java.util.regex.Pattern.compile("id=\"([^\"]+)\"").matcher(html);
        java.util.Set<String> ids = new java.util.HashSet<>();
        while (matcher.find()) {
            assertThat(ids.add(matcher.group(1)))
                    .as("unique anchor %s", matcher.group(1))
                    .isTrue();
        }
        assertThat(ids).hasSize(9);
        matcher = java.util.regex.Pattern.compile("href=\"#([^\"]+)\"").matcher(html);
        while (matcher.find()) {
            assertThat(ids).contains(matcher.group(1));
        }
    }

    @Test
    void rendersFootnotesWithWorkingReferencesAndBacklinksAfterSanitizing() {
        String rendered = renderer.render("正文[^memory]，再次引用[^memory]。\n\n[^memory]: 一次**硬件读写**。\n\n    第二段解释。");

        assertThat(rendered)
                .contains("<sup class=\"footnote-ref\">")
                .contains("href=\"#bd-fn-l6d656d6f7279\" id=\"bd-fnref-l6d656d6f7279\"")
                .contains("id=\"bd-fnref-l6d656d6f7279-2\"")
                .contains("<section class=\"footnotes\">")
                .contains("<li id=\"bd-fn-l6d656d6f7279\">")
                .contains("<strong>硬件读写</strong>")
                .contains("第二段解释。")
                .contains("href=\"#bd-fnref-l6d656d6f7279\"")
                .contains("href=\"#bd-fnref-l6d656d6f7279-2\"")
                .contains("class=\"footnote-backref\"")
                .contains("aria-label=\"Back to reference 1\"")
                .doesNotContain("[^memory]");
    }

    @Test
    void rendersInlineFootnotesAlongsideNamedFootnotesWithoutLeakingAcrossPosts() {
        assertThat(renderer.render("正文^[行内解释]，术语[^名词]。\n\n[^名词]: 命名解释。"))
                .contains("id=\"bd-fn-i1\"")
                .contains("id=\"bd-fn-le5908de8af8d\"")
                .contains("行内解释")
                .contains("命名解释。")
                .doesNotContain("^[行内解释]");
        assertThat(renderer.render("另一个正文^[独立解释]。")).contains("id=\"bd-fn-i1\"").doesNotContain("命名解释。");
        assertThat(renderer.render("普通正文")).doesNotContain("footnote");
    }

    @Test
    void preservesUndefinedReferencesAndCodeWhileKeepingFootnoteContentSafe() {
        assertThat(renderer.render("未知[^missing]，`[^code]`。\n\n[^unused]: 不应显示的定义。"))
                .contains("[^missing]", "<code>[^code]</code>")
                .doesNotContain("不应显示的定义。", "class=\"footnotes\"");
        assertThat(renderer.render("正文[^safe]。\n\n[^safe]: <script>alert(1)</script>\n\n    [危险](javascript:alert(1))"))
                .contains("&lt;script&gt;")
                .doesNotContain("<script>", "javascript:", "onclick=");
    }

    @Test
    void footnoteDomTextMatchesAnnotationTextRepresentation() {
        String markdown = "正文[^memory]，后续文字。\n\n行内^[补充]。\n\n[^memory]: 注释**内容**。";
        String html = renderer.render(markdown);
        String domText = manfred.bytedepth.app.post.MarkdownTextExtractor.renderedText(markdown);
        // DOM 模拟的 textContent 与服务端批注表示必须保留相同编号、空白与返回箭头。
        assertThat(org.springframework.web.util.HtmlUtils.htmlUnescape(html.replaceAll("<[^>]*>", "")))
                .isEqualTo(domText);
    }

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
        String rendered =
                renderer.render("# 标题\n\n```java\nint value = 1;\n```\n\n| 名称 | 值 |\n| --- | --- |\n| A | 1 |");

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

        assertThat(empty).contains("class=\"bd-code-block__line\">1</span>");
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
        String rendered = renderer.render("```java title:Example.java tabs:install\n"
                + "java code\n"
                + "```\n"
                + "```kotlin title:Example.kt tabs:install\n"
                + "kotlin code\n"
                + "```");

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
    void rendersObsidianGroupTabLabelsSeparatelyFromPanelTitles() {
        String rendered = renderer.render("```java group:install tab:Java title:Rule.java\njava code\n```\n"
                + "```python group:install tab:Python title:rule.py\npython code\n```");

        assertThat(rendered)
                .contains("class=\"bd-code-tabs\"")
                .containsPattern("bd-code-tabs__tab[^>]*>Java</button>")
                .containsPattern("bd-code-tabs__tab[^>]*>Python</button>")
                .contains("class=\"bd-code-block__title\">Rule.java</span>")
                .contains("class=\"bd-code-block__title\">rule.py</span>");
    }

    @Test
    void usesLanguageLabelsForTabsWithoutTitles() {
        String rendered =
                renderer.render("```java tabs:install\njava code\n```\n```kotlin tabs:install\nkotlin code\n```");

        assertThat(rendered).contains("bd-code-tabs__tab");
        assertThat(rendered).containsPattern("bd-code-tabs__tab[^>]*>java</button>");
        assertThat(rendered).containsPattern("bd-code-tabs__tab[^>]*>kotlin</button>");
    }

    @Test
    void usesLanguageForObsidianGroupWhenTabIsOmitted() {
        String rendered = renderer.render("```python group=install title=rule.py\npython code\n```\n"
                + "```go group=install tab=Go title=rule.go\ngo code\n```");

        assertThat(rendered)
                .containsPattern("bd-code-tabs__tab[^>]*>python</button>")
                .containsPattern("bd-code-tabs__tab[^>]*>Go</button>")
                .contains("class=\"bd-code-block__title\">rule.py</span>");
    }

    @Test
    void doesNotGroupExplicitTabBlocksAcrossParagraphsOrDifferentGroups() {
        String rendered = renderer.render("```java title:A.java tabs:first\n"
                + "a\n"
                + "```\n\n"
                + "说明\n\n"
                + "```kotlin title:B.kt tabs:first\n"
                + "b\n"
                + "```\n\n"
                + "```js title:C.js tabs:second\n"
                + "c\n"
                + "```");

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

        assertThat(rendered).contains("<h2 id=\"TDD 三定律与工作流\">TDD 三定律与工作流</h2>").contains("href=\"#TDD%20三定律与工作流\"");
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
