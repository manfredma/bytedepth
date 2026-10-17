package manfred.bytedepth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AnnotationMigrationFixtureTest {
    @Test
    void realMigrationIdentifiersFitMysqlWithTheFullIsolationPrefix() throws Exception {
        String prefix = "an_" + UUID.randomUUID().toString().replace("-", "");
        for (String migration : AnnotationMigrationFixture.MIGRATIONS) {
            try (var input = getClass().getResourceAsStream("/db/migration/" + migration)) {
                assertThat(input).isNotNull();
                String original = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                String sql = AnnotationMigrationFixture.namespacedSql(original, prefix);
                var identifiers = Pattern.compile("[a-zA-Z_][a-zA-Z_0-9]*").matcher(sql);
                while (identifiers.find()) {
                    assertThat(identifiers.group())
                            .as("MySQL identifier in %s", migration)
                            .hasSizeLessThanOrEqualTo(64);
                }
                for (String table : AnnotationMigrationFixture.TABLES) {
                    if (Pattern.compile("\\b" + table + "\\b").matcher(original).find()) {
                        assertThat(sql).contains(prefix + "_" + table);
                        assertThat(Pattern.compile("\\b" + table + "\\b")
                                        .matcher(sql)
                                        .find())
                                .isFalse();
                    }
                }
            }
        }
        assertThat(prefix + "_history").hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void indexNamesStayTableScopedAndColumnNamesRemainUnchanged() throws Exception {
        try (var input =
                getClass().getResourceAsStream("/db/migration/V21__upgrade_post_annotations_for_visibility.sql")) {
            assertThat(input).isNotNull();
            String sql = AnnotationMigrationFixture.namespacedSql(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8), "an_" + "a".repeat(32));
            assertThat(sql)
                    .contains("INDEX idx_post_annotation_visible ", "INDEX idx_post_annotation_owner_token ")
                    .contains("ADD COLUMN owner_token_hash CHAR(64)", "AFTER user_id");
        }
    }
}
