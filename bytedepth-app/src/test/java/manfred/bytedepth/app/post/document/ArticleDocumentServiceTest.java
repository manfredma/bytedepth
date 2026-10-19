package manfred.bytedepth.app.post.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArticleDocumentServiceTest {
    private final ArticleDocumentService service = new ArticleDocumentService();

    @Test
    void keepsLeadingImagesEagerAndDefersImagesAfterArticleContent() {
        for (String markdown : List.of(
                "![首图](/images/hero.svg)", "[![首图](/images/hero.svg)](/posts)", "**![首图](/images/hero.svg)**")) {
            assertThat(service.render(markdown).html())
                    .contains("loading=\"eager\"", "decoding=\"async\"", "src=\"/images/hero.svg\"");
        }
        for (String markdown : List.of(
                "介绍\n\n![图](/images/body.svg \"width=700\")",
                "# ![标题图](/images/title.svg)",
                "> ![引用图](/images/quote.svg)")) {
            assertThat(service.render(markdown).html()).contains("loading=\"lazy\"", "decoding=\"async\"");
        }
        assertThat(service.render("介绍\n\n![图](/images/body.svg \"width=700\")").html())
                .contains("width=\"700\"");
    }

    @Test
    void U13_codeControlsNeverEnterProjection() {
        var article = service.render("前文\n\n```java\nint anchorProbe = 1;\n```\n\n后文");
        assertThat(article.document().text()).isEqualTo("前文\nint anchorProbe = 1;\n后文");
        assertThat(article.document().segments())
                .extracting(s -> s.text())
                .containsExactly("前文", "int anchorProbe = 1;\n", "后文");
        assertThat(article.html()).contains("复制", "data-annotation-segment=\"s1\"");
    }

    @Test
    void U20_inlineMarkupAndImageAltDoNotPolluteText() {
        var article = service.render("**粗体**与[链接](https://example.com)及`a < b` ![不计入](a.png) &amp; &nbsp;");
        assertThat(article.document().text()).isEqualTo("粗体与链接及a < b  & \u00a0");
        assertThat(article.html())
                .contains("<strong>", "<code", "alt=\"不计入\"")
                .doesNotContain("data-annotation-segment=\"不计入");
    }

    @Test
    void U21_blocksTablesListsAndBreaksHaveExplicitSeparators() {
        assertThat(service.render("# 标题\n\n- 第一项\n- 第二项\n\n| A | B |\n|---|---|\n| C | D |\n\n软\n换  \n硬")
                        .document()
                        .text())
                .isEqualTo("标题\n第一项\n第二项\nA\tB\nC\tD\n软\n换\n硬");
    }

    @Test
    void U22_footnoteNavigationAndUnusedDefinitionsAreExcluded() {
        assertThat(service.render("正文[^n]，重复[^n]，行内^[解释]。\n\n[^n]: 注释**重点**。\n\n[^unused]: 隐藏")
                        .document()
                        .text())
                .isEqualTo("正文，重复，行内。\n注释重点。\n解释");
    }

    @Test
    void U22_undefinedFootnoteReferencesRemainSelectableLiteralText() {
        assertThat(service.render("未知[^missing]，`[^code]`。").document().text()).isEqualTo("未知[^missing]，[^code]。");
    }

    @Test
    void U24_U25_U26_unicodeIsCodepointExactAndOnlyLineEndingsNormalize() {
        var article = service.render("中😀e\u0301é👩‍💻🇨🇳️  &nbsp;\r\n\r\n```text\r\n\tA  B\r\n```");
        assertThat(article.document().text()).isEqualTo("中😀e\u0301é👩‍💻🇨🇳️  \u00a0\n\tA  B\n");
        for (var segment : article.document().segments()) {
            assertThat(segment.end() - segment.start())
                    .isEqualTo(segment.text().codePointCount(0, segment.text().length()));
        }
        assertThat(service.render("é").document().digest())
                .isNotEqualTo(service.render("e\u0301").document().digest());
        assertThat(service.render("中😀").document().segments().getFirst().end()).isEqualTo(2);
    }

    @Test
    void U27_escapedHtmlCannotInjectInternalMappingOrScripts() {
        var article = service.render("<span data-annotation-segment=\"s0\"><script>bad()</script></span>");
        assertThat(article.html()).doesNotContain("<script>", "<span data-annotation-segment=\"s0\"><script>");
        assertThat(article.html()).contains("&lt;span", "&lt;script&gt;");
    }

    @Test
    void U17_mermaidSourceIsTypedSeparatelyFromSelectableCode() {
        var article = service.render("前\n\n```mermaid\ngraph TD; A-->B\n```\n\n后");
        assertThat(article.document().segments())
                .extracting(s -> s.kind())
                .containsExactly("prose", "mermaid", "prose");
        assertThat(article.document().text()).isEqualTo("前\ngraph TD; A-->B\n后");
    }

    @Test
    void U28_modelIsDeterministicAndCollectionsCannotBeMutated() {
        var first = service.render("a **b** c").document();
        assertThat(service.render("a **b** c").document()).isEqualTo(first);
        assertThat(service.render("other").document()).isNotEqualTo(first);
        assertThat(first.digest()).matches("[0-9a-f]{64}");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> first.segments().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(service.render(null).document().segments()).isEqualTo(List.of());
        assertThat(service.render(" ").html()).isEmpty();
    }

    @Test
    void U28_renderedModelAndHtmlMatchReviewedCrossLanguageGoldens() throws Exception {
        var root = java.nio.file.Path.of("../scripts/test-fixtures/annotation");
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var assertions = new java.util.ArrayList<org.junit.jupiter.api.function.Executable>();
        for (String name : List.of("rich", "unicode", "duplicates", "footnotes")) {
            var actual = service.render(java.nio.file.Files.readString(root.resolve(name + "-markdown.md")));
            var diagnostic = java.nio.file.Path.of("target/annotation-golden-actual");
            java.nio.file.Files.createDirectories(diagnostic);
            java.nio.file.Files.writeString(diagnostic.resolve(name + "-page.html"), actual.html());
            java.nio.file.Files.writeString(
                    diagnostic.resolve(name + "-document.json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(actual.document()));
            tools.jackson.databind.JsonNode actualModel = json.valueToTree(actual.document());
            assertions.add(() -> assertThat(actualModel)
                    .as(name + " explicit model")
                    .isEqualTo(json.readTree(java.nio.file.Files.readString(root.resolve(name + "-document.json")))));
            assertions.add(() -> assertThat(actual.html())
                    .as(name + " sanitized HTML")
                    .isEqualTo(java.nio.file.Files.readString(root.resolve(name + "-page.html"))));
        }
        org.junit.jupiter.api.Assertions.assertAll(assertions);
    }

    @Test
    void U21_emptyAndIndentedCodeKeepExactSourceWithoutInventingLeadingText() {
        assertThat(service.render("```\n```\n\n后").document().text()).isEqualTo("后");
        var indented = service.render("    raw\t  value\n");
        assertThat(indented.document().text()).isEqualTo("raw\t  value\n");
        assertThat(indented.document().segments().getFirst().kind()).isEqualTo("code");
        assertThat(service.render("> ```\n> nested\n> ```").document().text()).isEqualTo("nested\n");
        assertThat(service.render("> ```mermaid\n> graph TD\n> ```")
                        .document()
                        .segments()
                        .getFirst()
                        .kind())
                .isEqualTo("mermaid");
    }

    @Test
    void U28_digestFailureAbortsModelInsteadOfPublishingUnverifiableIdentity() throws Exception {
        try (var crypto = org.mockito.Mockito.mockStatic(java.security.MessageDigest.class)) {
            crypto.when(() -> java.security.MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new java.security.NoSuchAlgorithmException("provider unavailable"));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.render("正文"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(java.security.NoSuchAlgorithmException.class);
        }
    }
}
