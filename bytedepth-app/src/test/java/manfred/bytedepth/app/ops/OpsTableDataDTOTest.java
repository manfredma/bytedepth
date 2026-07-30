package manfred.bytedepth.app.ops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsTableDataDTOTest {

    @Test
    void constructor_exposesOnlyDeclaredColumns() {
        OpsTableDataDTO data = new OpsTableDataDTO("user", List.of("id", "username"), List.of(Map.of(
                "id", 7L,
                "username", "admin",
                "password", "db-secret",
                "redisValue", "cached-secret",
                "connectionUrl", "jdbc:mysql://db/bytedepth")));

        Map<String, Object> row = data.rows().get(0);

        assertEquals(7L, row.get("id"));
        assertEquals("admin", row.get("username"));
        assertFalse(row.containsKey("password"));
        assertFalse(row.containsKey("redisValue"));
        assertFalse(row.containsKey("connectionUrl"));
    }
}
