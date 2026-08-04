package manfred.bytedepth.adapter.web.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisitRequestFilterTest {

    @Test
    void shouldReject_whenUserAgentMatchesCaseInsensitivePrefixRule() {
        VisitFilterProperties properties = properties(newRule(
                VisitFilterProperties.Field.USER_AGENT,
                VisitFilterProperties.Match.PREFIX,
                "Python-urllib"));

        assertThat(new VisitRequestFilter(properties)
                .shouldRecord(new VisitRequestFilter.Request("python-urllib/3.12")))
                .isFalse();
    }

    @Test
    void shouldReject_whenUserAgentMatchesContainsRule() {
        VisitFilterProperties properties = properties(newRule(
                VisitFilterProperties.Field.USER_AGENT,
                VisitFilterProperties.Match.CONTAINS,
                "crawler"));

        assertThat(new VisitRequestFilter(properties)
                .shouldRecord(new VisitRequestFilter.Request("ExampleCrawler/1.0")))
                .isFalse();
    }

    @Test
    void shouldRecord_whenUserAgentIsMissingOrDoesNotMatch() {
        VisitRequestFilter filter = new VisitRequestFilter(properties(newRule(
                VisitFilterProperties.Field.USER_AGENT,
                VisitFilterProperties.Match.PREFIX,
                "curl/")));

        assertThat(filter.shouldRecord(new VisitRequestFilter.Request(null))).isTrue();
        assertThat(filter.shouldRecord(new VisitRequestFilter.Request(" "))).isTrue();
        assertThat(filter.shouldRecord(new VisitRequestFilter.Request("Mozilla/5.0"))).isTrue();
    }

    @Test
    void shouldRecord_whenFilteringIsDisabled() {
        VisitFilterProperties properties = properties(newRule(
                VisitFilterProperties.Field.USER_AGENT,
                VisitFilterProperties.Match.PREFIX,
                "curl/"));
        properties.setEnabled(false);

        assertThat(new VisitRequestFilter(properties)
                .shouldRecord(new VisitRequestFilter.Request("curl/8.0")))
                .isTrue();
    }

    @Test
    void shouldApplyCaseSensitiveRegularExpressions() {
        VisitFilterProperties.Rule rule = newRule(
                VisitFilterProperties.Field.USER_AGENT,
                VisitFilterProperties.Match.REGEX,
                "Bot-[0-9]+");
        rule.setIgnoreCase(false);
        VisitRequestFilter filter = new VisitRequestFilter(properties(rule));

        assertThat(filter.shouldRecord(new VisitRequestFilter.Request("Bot-42"))).isFalse();
        assertThat(filter.shouldRecord(new VisitRequestFilter.Request("bot-42"))).isTrue();
    }

    @Test
    void shouldApplyCaseInsensitiveRegularExpressions() {
        VisitRequestFilter filter = new VisitRequestFilter(properties(newRule(
                VisitFilterProperties.Field.USER_AGENT,
                VisitFilterProperties.Match.REGEX,
                "Bot-[0-9]+")));

        assertThat(filter.shouldRecord(new VisitRequestFilter.Request("bot-42"))).isFalse();
    }

    private VisitFilterProperties properties(VisitFilterProperties.Rule rule) {
        VisitFilterProperties properties = new VisitFilterProperties();
        properties.setRules(java.util.List.of(rule));
        return properties;
    }

    private VisitFilterProperties.Rule newRule(VisitFilterProperties.Field field,
                                               VisitFilterProperties.Match match,
                                               String value) {
        VisitFilterProperties.Rule rule = new VisitFilterProperties.Rule();
        rule.setId("test-rule");
        rule.setField(field);
        rule.setMatch(match);
        rule.setValue(value);
        return rule;
    }
}
