package manfred.bytedepth.adapter.web.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class UtilityCoverageTest {

    @Test
    void markdownUtilitiesHandleEmptyContentAndEscapeNonHighlightMarkup() {
        MarkdownRenderer renderer = new MarkdownRenderer();

        assertThat(renderer.render(null)).isEmpty();
        assertThat(renderer.render(" \n")).isEmpty();
        assertThat(renderer.countVisibleCharacters(null)).isZero();
        assertThat(new MarkdownExcerpt().excerpt("# Heading\n\ncontent", 100)).isEqualTo("content");
        assertThat(new SearchHighlight().title("<script>x</script><em>safe</em>"))
                .isEqualTo("&lt;script&gt;x&lt;/script&gt;<em>safe</em>");
        assertThat(new SearchHighlight().snippet("<em>safe</em>")).isEqualTo("<em>safe</em>");
        assertThat(new SearchHighlight().title(null)).isEmpty();
    }

    @Test
    void seoExcerptCoversAllMarkdownCleanupAndTruncationBranches() {
        String markdown = "# Heading\n> **bold** [link](https://example.com) `code` ![image](x)\n---\n```java\nignored\n```";

        assertThat(SeoUtils.excerpt(null)).isEmpty();
        assertThat(SeoUtils.excerpt("   ")).isEmpty();
        assertThat(SeoUtils.excerpt(markdown, 200)).isEqualTo("Heading bold link");
        assertThat(SeoUtils.excerpt("one two three four", 9)).isEqualTo("one two…");
        assertThat(SeoUtils.excerpt("abcdefghij", 4)).isEqualTo("abcd…");
        assertThat(SeoUtils.excerpt("short")).isEqualTo("short");
    }

    @Test
    void visitRulesValidateConfigurationAndExposeAllMatchingModes() {
        VisitFilterProperties properties = new VisitFilterProperties();
        VisitFilterProperties.Rule rule = rule("bot", VisitFilterProperties.Match.EXACT, "Bot");
        properties.setRules(List.of(rule));
        properties.validate();
        assertThat(properties.getRules()).containsExactly(rule);
        assertThat(rule.getField()).isEqualTo(VisitFilterProperties.Field.USER_AGENT);
        assertThat(rule.isIgnoreCase()).isTrue();
        assertThat(rule.isEnabled()).isTrue();

        assertThat(new VisitRequestFilter(properties).shouldRecord(new VisitRequestFilter.Request("bot"))).isFalse();
        rule.setMatch(VisitFilterProperties.Match.REGEX);
        rule.setValue("bot-[0-9]+");
        rule.setIgnoreCase(false);
        assertThat(new VisitRequestFilter(properties).shouldRecord(new VisitRequestFilter.Request("bot-42"))).isFalse();
        assertThat(new VisitRequestFilter(properties).shouldRecord(new VisitRequestFilter.Request("BOT-42"))).isTrue();

        VisitFilterProperties.Rule disabled = rule("disabled", VisitFilterProperties.Match.EXACT, "ignored");
        disabled.setEnabled(false);
        properties.setRules(List.of(disabled));
        assertThat(new VisitRequestFilter(properties).shouldRecord(new VisitRequestFilter.Request("ignored"))).isTrue();
    }

    @Test
    void visitRulesRejectEveryInvalidConfigurationBranch() {
        VisitFilterProperties validRegexProperties = new VisitFilterProperties();
        validRegexProperties.setRules(List.of(rule("valid-regex", VisitFilterProperties.Match.REGEX, "bot-[0-9]+")));
        assertThatCode(validRegexProperties::validate).doesNotThrowAnyException();

        assertInvalid(rule(null, VisitFilterProperties.Match.EXACT, "x"), "必须配置 id");
        assertInvalid(rule("incomplete", null, "x"), "配置不完整");
        assertInvalid(rule("blank", VisitFilterProperties.Match.EXACT, " "), "配置不完整");
        assertInvalid(rule("invalid-regex", VisitFilterProperties.Match.REGEX, "["), "正则表达式非法");

        assertInvalid(rule(" ", VisitFilterProperties.Match.EXACT, "x"), "必须配置 id");
        VisitFilterProperties.Rule missingField = rule("missing-field", VisitFilterProperties.Match.EXACT, "x");
        missingField.setField(null);
        assertInvalid(missingField, "配置不完整");
        VisitFilterProperties.Rule missingValue = rule("missing-value", VisitFilterProperties.Match.EXACT, "x");
        missingValue.setValue(null);
        assertInvalid(missingValue, "配置不完整");

        VisitFilterProperties properties = new VisitFilterProperties();
        properties.setRules(List.of(
                rule("duplicate", VisitFilterProperties.Match.EXACT, "one"),
                rule("duplicate", VisitFilterProperties.Match.EXACT, "two")));
        assertThatIllegalStateException().isThrownBy(properties::validate).withMessageContaining("id 重复");
    }

    private static VisitFilterProperties.Rule rule(String id, VisitFilterProperties.Match match, String value) {
        VisitFilterProperties.Rule rule = new VisitFilterProperties.Rule();
        rule.setId(id);
        rule.setField(VisitFilterProperties.Field.USER_AGENT);
        rule.setMatch(match);
        rule.setValue(value);
        return rule;
    }

    private static void assertInvalid(VisitFilterProperties.Rule rule, String message) {
        VisitFilterProperties properties = new VisitFilterProperties();
        properties.setRules(List.of(rule));
        assertThatIllegalStateException().isThrownBy(properties::validate).withMessageContaining(message);
    }
}
