package manfred.bytedepth.app.ops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsCoverageTest {

    @Test
    void statusDtos_normalizeUnavailableErrorsForBothPaths() {
        assertNull(new OpsDatabaseStatusDTO(true, "db").error());
        assertEquals("MySQL health check failed", new OpsDatabaseStatusDTO(false, "db").error());
        assertFalse(OpsDatabaseStatusDTO.unavailable().available());
        assertNull(new OpsRedisStatusDTO(true, "1B", 1, 2, 3, 4, 5).error());
        assertEquals("Redis health check failed", new OpsRedisStatusDTO(false, null, 0, 0, 0, 0, 0).error());
        assertFalse(OpsRedisStatusDTO.unavailable().available());
        assertNull(new OpsMeiliSearchStatusDTO(true, true).error());
        assertEquals("MeiliSearch health check failed", new OpsMeiliSearchStatusDTO(true, false).error());
        assertFalse(OpsMeiliSearchStatusDTO.unavailable().healthAvailable());
        assertEquals("UNAVAILABLE", OpsDeploymentStatusDTO.unavailable().state());
    }

    @Test
    void tableWhitelistAndStatusQuery_delegateOnlyApprovedOperations() {
        assertEquals("post", OpsTable.fromName("post").tableName());
        assertEquals(List.of("id", "post_id", "author_id", "content", "created_at"), OpsTable.COMMENT.columns());
        assertThrows(IllegalArgumentException.class, () -> OpsTable.fromName("missing"));

        OpsTableDataPort data = mock(OpsTableDataPort.class);
        OpsTableDataDTO expected = new OpsTableDataDTO("post", List.of("id"), List.of(Map.of("id", 1L)));
        when(data.list(OpsTable.POST)).thenReturn(expected);
        assertSame(expected, new OpsTableQryExe(data).execute("post"));

        OpsDeploymentPort deployment = mock(OpsDeploymentPort.class);
        OpsDeploymentStatusDTO status = OpsDeploymentStatusDTO.unavailable();
        when(deployment.status()).thenReturn(status);
        assertSame(status, new OpsDeploymentStatusQryExe(deployment).execute());
    }

    @Test
    void overview_isolatesEachOfTheThreeFailingDependencies() {
        OpsOverviewDTO overview = new OpsOverviewQryExe(
                () -> { throw new IllegalStateException(); },
                () -> { throw new IllegalStateException(); },
                () -> { throw new IllegalStateException(); }).execute();
        assertFalse(overview.database().available());
        assertFalse(overview.redis().available());
        assertFalse(overview.meiliSearch().healthAvailable());
    }
}
