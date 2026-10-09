package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsDatabasePort;
import manfred.bytedepth.app.ops.OpsDatabaseStatusDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcOpsDatabaseAdapter implements OpsDatabasePort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOpsDatabaseAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OpsDatabaseStatusDTO inspect() {
        String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        return new OpsDatabaseStatusDTO(true, databaseName);
    }
}
