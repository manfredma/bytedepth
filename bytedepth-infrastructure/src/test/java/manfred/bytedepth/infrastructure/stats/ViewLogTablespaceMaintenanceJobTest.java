package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.app.analytics.ViewLogArchiveSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewLogTablespaceMaintenanceJobTest {

    @Test
    void doesNotOptimizeWhenBothDeletionAndFragmentationThresholdsAreBelowLimit() {
        var mapper = mock(ViewLogTablespaceMaintenanceMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);
        when(mapper.findState(ViewLogArchiveSource.POST)).thenReturn(new ViewLogTablespaceState(99));
        when(mapper.findMetrics(ViewLogArchiveSource.POST)).thenReturn(new ViewLogTablespaceMetrics(1_000, 99));

        new ViewLogTablespaceMaintenanceJob(mapper, jdbcTemplate,
                new ViewLogTablespaceMaintenanceProperties(100, 100)).runMaintenance();

        verify(jdbcTemplate, never()).update("OPTIMIZE TABLE post_view_log");
        verify(mapper, never()).resetDeletedRows(ViewLogArchiveSource.POST);
    }

    @Test
    void treatsMissingMaintenanceStateAndMetricsAsZero() {
        var mapper = mock(ViewLogTablespaceMaintenanceMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);

        new ViewLogTablespaceMaintenanceJob(mapper, jdbcTemplate,
                new ViewLogTablespaceMaintenanceProperties(100, 100)).runMaintenance();

        verify(jdbcTemplate, never()).update("OPTIMIZE TABLE post_view_log");
        verify(jdbcTemplate, never()).update("OPTIMIZE TABLE page_view_log");
    }

    @Test
    void stateAndMetricsBeansExposeTheirMappedValues() {
        var state = new ViewLogTablespaceState();
        state.setDeletedRowsSinceOptimize(42);
        assertEquals(42, state.getDeletedRowsSinceOptimize());

        var metrics = new ViewLogTablespaceMetrics();
        metrics.setDataLength(1000);
        metrics.setDataFreeBytes(200);
        assertEquals(1000, metrics.getDataLength());
        assertEquals(200, metrics.getDataFreeBytes());
    }

    @Test
    void optimizesEachSourceAtOrAboveThresholdWithFixedIdentifiers() {
        var mapper = mock(ViewLogTablespaceMaintenanceMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);
        when(mapper.findState(ViewLogArchiveSource.POST)).thenReturn(new ViewLogTablespaceState(100));
        when(mapper.findState(ViewLogArchiveSource.PAGE)).thenReturn(new ViewLogTablespaceState(0));
        when(mapper.findMetrics(ViewLogArchiveSource.POST)).thenReturn(new ViewLogTablespaceMetrics(1_000, 0));
        when(mapper.findMetrics(ViewLogArchiveSource.PAGE)).thenReturn(new ViewLogTablespaceMetrics(1_000, 100));

        new ViewLogTablespaceMaintenanceJob(mapper, jdbcTemplate,
                new ViewLogTablespaceMaintenanceProperties(100, 100)).runMaintenance();

        verify(jdbcTemplate).update("OPTIMIZE TABLE post_view_log");
        verify(jdbcTemplate).update("OPTIMIZE TABLE page_view_log");
        verify(mapper).resetDeletedRows(ViewLogArchiveSource.POST);
        verify(mapper).resetDeletedRows(ViewLogArchiveSource.PAGE);
    }

    @Test
    void failedSourceIsNotResetButOtherSourceStillRuns() {
        var mapper = mock(ViewLogTablespaceMaintenanceMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);
        when(mapper.findState(ViewLogArchiveSource.POST)).thenReturn(new ViewLogTablespaceState(100));
        when(mapper.findState(ViewLogArchiveSource.PAGE)).thenReturn(new ViewLogTablespaceState(100));
        when(mapper.findMetrics(ViewLogArchiveSource.POST)).thenReturn(new ViewLogTablespaceMetrics(1_000, 0));
        when(mapper.findMetrics(ViewLogArchiveSource.PAGE)).thenReturn(new ViewLogTablespaceMetrics(1_000, 0));
        when(jdbcTemplate.update("OPTIMIZE TABLE post_view_log"))
                .thenThrow(new IllegalStateException("post optimize failed"));

        new ViewLogTablespaceMaintenanceJob(mapper, jdbcTemplate,
                new ViewLogTablespaceMaintenanceProperties(100, 100)).runMaintenance();

        verify(mapper, never()).resetDeletedRows(ViewLogArchiveSource.POST);
        verify(jdbcTemplate).update("OPTIMIZE TABLE page_view_log");
        verify(mapper).resetDeletedRows(ViewLogArchiveSource.PAGE);
    }

    @Test
    void nextRunRetriesTheSourceThatWasNotReset() {
        var mapper = mock(ViewLogTablespaceMaintenanceMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);
        when(mapper.findState(ViewLogArchiveSource.POST)).thenReturn(new ViewLogTablespaceState(100));
        when(mapper.findMetrics(ViewLogArchiveSource.POST)).thenReturn(new ViewLogTablespaceMetrics(1_000, 0));
        when(jdbcTemplate.update("OPTIMIZE TABLE post_view_log"))
                .thenThrow(new IllegalStateException("post optimize failed"))
                .thenReturn(0);
        var job = new ViewLogTablespaceMaintenanceJob(mapper, jdbcTemplate,
                new ViewLogTablespaceMaintenanceProperties(100, 100));

        job.runMaintenance();
        job.runMaintenance();

        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update("OPTIMIZE TABLE post_view_log");
        verify(mapper).resetDeletedRows(ViewLogArchiveSource.POST);
    }

    @Test
    void usesDedicatedWeeklyShanghaiSchedule() throws NoSuchMethodException {
        var scheduled = ViewLogTablespaceMaintenanceJob.class.getMethod("run").getAnnotation(Scheduled.class);

        assertEquals("Asia/Shanghai", scheduled.zone());
        assertEquals("viewLogTablespaceScheduler", scheduled.scheduler());
        assertEquals("${bytedepth.analytics.optimize-cron:0 30 3 ? * SUN}", scheduled.cron());
    }

    @Test
    void skipsMaintenanceWhenTheDedicatedNamedLockIsUnavailable() throws Exception {
        var mapper = mock(ViewLogTablespaceMaintenanceMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);
        var connection = mock(Connection.class);
        var lockStatement = mock(PreparedStatement.class);
        var lockResult = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(lockStatement);
        when(lockStatement.executeQuery()).thenReturn(lockResult);
        when(lockResult.next()).thenReturn(true);
        when(lockResult.getInt(1)).thenReturn(0);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation ->
                ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection));
        var job = new ViewLogTablespaceMaintenanceJob(mapper, jdbcTemplate,
                new ViewLogTablespaceMaintenanceProperties(100, 100, "custom-lock"));

        job.run();

        verify(mapper, never()).findState(any());
        verify(lockStatement).setString(1, "custom-lock");
    }

    @Test
    void skipsMaintenanceWhenNamedLockReturnsNoRow() throws Exception {
        var mapper = mock(ViewLogTablespaceMaintenanceMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);
        var connection = mock(Connection.class);
        var lockStatement = mock(PreparedStatement.class);
        var lockResult = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(lockStatement);
        when(lockStatement.executeQuery()).thenReturn(lockResult);
        when(lockResult.next()).thenReturn(false);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation ->
                ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection));

        new ViewLogTablespaceMaintenanceJob(mapper, jdbcTemplate,
                new ViewLogTablespaceMaintenanceProperties(100, 100)).run();

        verify(mapper, never()).findState(any());
    }

    @Test
    void releasesDedicatedNamedLockAfterMaintenance() throws Exception {
        var mapper = mock(ViewLogTablespaceMaintenanceMapper.class);
        var jdbcTemplate = mock(JdbcTemplate.class);
        var connection = mock(Connection.class);
        var lockStatement = mock(PreparedStatement.class);
        var releaseStatement = mock(PreparedStatement.class);
        var lockResult = mock(ResultSet.class);
        var releaseResult = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(lockStatement);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(releaseStatement);
        when(lockStatement.executeQuery()).thenReturn(lockResult);
        when(releaseStatement.executeQuery()).thenReturn(releaseResult);
        when(lockResult.next()).thenReturn(true);
        when(lockResult.getInt(1)).thenReturn(1);
        when(releaseResult.next()).thenReturn(true);
        when(releaseResult.getInt(1)).thenReturn(1);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation ->
                ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection));
        var job = new ViewLogTablespaceMaintenanceJob(mapper, jdbcTemplate,
                new ViewLogTablespaceMaintenanceProperties(100, 100));

        job.run();

        verify(releaseStatement).setString(1, "bytedepth:view-log-tablespace");
    }

    @Test
    void rejectsInvalidMaintenanceThresholds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ViewLogTablespaceMaintenanceProperties(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ViewLogTablespaceMaintenanceProperties(1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ViewLogTablespaceMaintenanceProperties(1, 0, " "));
        assertEquals("bytedepth:view-log-tablespace",
                new ViewLogTablespaceMaintenanceProperties(1, 0, null).optimizeLockName());
    }
}
