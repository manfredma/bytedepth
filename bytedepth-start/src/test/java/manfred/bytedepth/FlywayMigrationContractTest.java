package manfred.bytedepth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FlywayMigrationContractTest {

    @Test
    void readingHistoryMigrationIsAdditive() throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/V27__add_reading_history.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).doesNotContain("DROP TABLE", "DROP COLUMN", "DELETE FROM");
        }
    }
}
