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
        assertThat(metadata.enhanced()).isFalse();
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
        assertThat(metadata.enhanced()).isTrue();
    }

    @Test
    void parsesFoldAndExplicitTabGroup() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java fold tabs:install");

        assertThat(metadata.fold()).isTrue();
        assertThat(metadata.tabGroup()).contains("install");
        assertThat(metadata.enhanced()).isTrue();
    }

    @Test
    void ignoresUnknownAndMalformedParameters() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse("java unknown:value title: tabs:");

        assertThat(metadata.title()).isEmpty();
        assertThat(metadata.tabGroup()).isEmpty();
        assertThat(metadata.enhanced()).isFalse();
    }

    @Test
    void handlesMissingInfo() {
        CodeBlockMetadata metadata = CodeBlockMetadata.parse(null);

        assertThat(metadata.language()).isEmpty();
        assertThat(metadata.title()).isEmpty();
        assertThat(metadata.fold()).isFalse();
        assertThat(metadata.tabGroup()).isEmpty();
        assertThat(metadata.enhanced()).isFalse();
    }
}
