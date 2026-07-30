package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsTable;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcOpsTableDataAdapterTest {

    @Test
    void list_usesTheFixedWhitelistedPostQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        String query = "SELECT id, title, status, author_id, created_at, updated_at FROM `post` ORDER BY id DESC LIMIT 50";
        when(jdbcTemplate.queryForList(query)).thenReturn(List.of(Map.of("id", 1L, "title", "Post")));

        var result = new JdbcOpsTableDataAdapter(jdbcTemplate).list(OpsTable.POST);

        assertEquals("post", result.tableName());
        assertEquals(6, result.columns().size());
        assertEquals(1, result.rows().size());
    }
}
