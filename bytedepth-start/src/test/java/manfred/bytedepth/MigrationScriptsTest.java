package manfred.bytedepth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MigrationScriptsTest {

    @Test
    void seriesOwnershipMigrationUsesAdminThenExistingUserAsOwner() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V18__add_series_author.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("admin_owner", "fallback_owner", "COALESCE(admin_owner.id, fallback_owner.id)");
            assertThat(sql).contains("MODIFY COLUMN `author_id` BIGINT NOT NULL");
        }
    }

    @Test
    void contentVersionMigrationInitializesRowsFromTimestamps() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V24__add_post_content_version.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("ADD COLUMN content_version INT NOT NULL DEFAULT 1");
            assertThat(sql).contains("WHEN updated_at <> created_at THEN 2");
            assertThat(sql).contains("ELSE 1");
        }
    }
}
