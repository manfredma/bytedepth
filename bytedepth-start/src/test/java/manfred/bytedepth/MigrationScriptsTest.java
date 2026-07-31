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
}
