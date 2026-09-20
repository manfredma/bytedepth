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
    void viewLogArchiveMigrationIsAdditiveAndDefinesAllAggregateKeys() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V25__add_view_log_archive_stats.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains(
                "CREATE TABLE post_view_hourly_stat",
                "CREATE TABLE page_view_hourly_stat",
                "CREATE TABLE post_view_country_daily_stat",
                "CREATE TABLE page_view_country_daily_stat",
                "CREATE TABLE view_log_archive_bucket",
                "CREATE TABLE view_log_tablespace_state",
                "PRIMARY KEY (stat_hour, post_id)",
                "PRIMARY KEY (stat_hour, page_path)",
                "PRIMARY KEY (stat_date, post_id, country)",
                "PRIMARY KEY (stat_date, page_path, country)",
                "PRIMARY KEY (source, bucket_start)",
                "PRIMARY KEY (source)",
                "deleted_rows_since_optimize");
        assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM");
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
