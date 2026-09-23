package manfred.bytedepth.adapter.web.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeBlockMetadataTest {

    @Test
    void parsesLanguageOnlyInfoAsAnOrdinaryCodeBlock() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java");

        assertThat(metadata.language()).isEqualTo("java");
        assertThat(metadata.title()).isEmpty();
        assertThat(metadata.fold()).isFalse();
        assertThat(metadata.tabGroup()).isEmpty();
    }

    @Test
    void parsesTitleAndFileAliases() {
        assertThat(CodeBlockMetadata.parse("java title:Example.java").title())
                .contains("Example.java");
        assertThat(CodeBlockMetadata.parse("java file:Example.java").title())
                .contains("Example.java");
    }

    @Test
    void parsesQuotedTitleWithSpaces() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java title:\"Example Main.java\"");

        assertThat(metadata.title()).contains("Example Main.java");
    }

    @Test
    void parsesFoldAndExplicitTabGroup() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java fold tabs:install");

        assertThat(metadata.fold()).isTrue();
        assertThat(metadata.tabGroup()).contains("install");
    }

    @Test
    void parsesTabGroupWithoutFold() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java tabs:install");

        assertThat(metadata.fold()).isFalse();
        assertThat(metadata.tabGroup()).contains("install");
    }

    @Test
    void ignoresUnknownAndMalformedParameters() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java unknown:value title: tabs:");

        assertThat(metadata.title()).isEmpty();
        assertThat(metadata.tabGroup()).isEmpty();
    }

    @Test
    void ignoresParametersWithNoKey() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java :value");

        assertThat(metadata.title()).isEmpty();
        assertThat(metadata.tabGroup()).isEmpty();
    }

    @Test
    void ignoresParametersWithBlankQuotedValues() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java title:\"\" tabs:\"\"");

        assertThat(metadata.title()).isEmpty();
        assertThat(metadata.tabGroup()).isEmpty();
    }

    @Test
    void ignoresUnclosedQuotedValues() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java title:\"Unclosed");

        assertThat(metadata.title()).isEmpty();
    }

    @Test
    void ignoresQuotedValuesWithOnlyATrailingQuote() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java title:Unclosed\"");

        assertThat(metadata.title()).isEmpty();
    }

    @Test
    void ignoresSingleQuoteValues() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java title:\"");

        assertThat(metadata.title()).isEmpty();
    }

    @Test
    void keepsBackslashesOutsideQuotedValues() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java title:Example\\Path");

        assertThat(metadata.title()).contains("Example\\Path");
    }

    @Test
    void keepsTheFirstTitleWhenTitleIsRepeated() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java title:First.java title:Second.java");

        assertThat(metadata.title()).contains("First.java");
    }

    @Test
    void toleratesRepeatedWhitespaceBetweenParameters() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java  fold");

        assertThat(metadata.fold()).isTrue();
    }

    @Test
    void unescapesQuotesInsideQuotedValues() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java title:\"Example \\\"Main.java\"");

        assertThat(metadata.title()).contains("Example \"Main.java");
    }

    @Test
    void handlesMissingInfo() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse(null);

        assertThat(metadata.language()).isEmpty();
        assertThat(metadata.title()).isEmpty();
        assertThat(metadata.fold()).isFalse();
        assertThat(metadata.tabGroup()).isEmpty();

        assertThat(CodeBlockMetadata.parse(" \t").language()).isEmpty();
    }
}
