package manfred.bytedepth.app.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsOverviewQryExeTest {

    @Test
    void execute_isolatesAFailedDependencyAndReturnsOtherServiceStates() {
        OpsDatabasePort database = () -> new OpsDatabaseStatusDTO(true, "bytedepth");
        OpsRedisPort redis = () -> {
            throw new IllegalStateException("Redis unavailable");
        };
        OpsMeiliSearchPort meiliSearch = () -> new OpsMeiliSearchStatusDTO(true, true);

        OpsOverviewDTO overview = new OpsOverviewQryExe(database, redis, meiliSearch).execute();

        assertTrue(overview.database().available());
        assertFalse(overview.redis().available());
        assertTrue(overview.meiliSearch().healthAvailable());
        assertTrue(overview.meiliSearch().statsAvailable());
        assertTrue(overview.uptimeMillis() >= 0);
    }
}
