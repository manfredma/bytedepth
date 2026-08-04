package manfred.bytedepth.infrastructure.ops;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcOpsDatabaseAdapterTest {

    @Test
    void returnsTheConnectedDatabaseName() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("bytedepth");

        var status = new JdbcOpsDatabaseAdapter(jdbc).inspect();

        assertEquals("bytedepth", status.databaseName());
    }
}
