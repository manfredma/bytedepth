package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsTable;
import manfred.bytedepth.app.ops.OpsTableDataDTO;
import manfred.bytedepth.app.ops.OpsTableDataPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class JdbcOpsTableDataAdapter implements OpsTableDataPort {

    private static final Map<OpsTable, String> QUERIES = Map.of(
            OpsTable.POST, "SELECT id, title, status, author_id, created_at, updated_at FROM `post` ORDER BY id DESC LIMIT 50",
            OpsTable.COMMENT, "SELECT id, post_id, author_id, content, created_at FROM `comment` ORDER BY id DESC LIMIT 50",
            OpsTable.USER, "SELECT id, username, email, status, created_at, updated_at FROM `user` ORDER BY id DESC LIMIT 50"
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcOpsTableDataAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OpsTableDataDTO list(OpsTable table) {
        if (table == null) {
            throw new IllegalArgumentException("Unsupported operations table");
        }
        String query = QUERIES.get(table);
        if (query == null) {
            throw new IllegalArgumentException("Unsupported operations table");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
        return new OpsTableDataDTO(table.tableName(), table.columns(), rows);
    }
}
