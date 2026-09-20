package manfred.bytedepth.infrastructure.stats;

import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.app.analytics.ViewLogArchiveSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
@Component
public class ViewLogTablespaceMaintenanceJob {

    private static final String POST_OPTIMIZE_SQL = "OPTIMIZE TABLE post_view_log";
    private static final String PAGE_OPTIMIZE_SQL = "OPTIMIZE TABLE page_view_log";

    private final ViewLogTablespaceMaintenanceMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final ViewLogTablespaceMaintenanceProperties properties;

    public ViewLogTablespaceMaintenanceJob(ViewLogTablespaceMaintenanceMapper mapper,
                                            JdbcTemplate jdbcTemplate,
                                            ViewLogTablespaceMaintenanceProperties properties) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Scheduled(cron = "${bytedepth.analytics.optimize-cron:0 30 3 ? * SUN}",
            zone = "Asia/Shanghai", scheduler = "viewLogTablespaceScheduler")
    public void run() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            if (!callLockFunction(connection, "SELECT GET_LOCK(?, 0)", properties.optimizeLockName())) {
                log.info("View log tablespace maintenance skipped because the named lock is unavailable");
                return null;
            }
            try {
                runMaintenance();
                return null;
            } finally {
                callLockFunction(connection, "SELECT RELEASE_LOCK(?)", properties.optimizeLockName());
            }
        });
    }

    void runMaintenance() {
        maintain(ViewLogArchiveSource.POST, POST_OPTIMIZE_SQL);
        maintain(ViewLogArchiveSource.PAGE, PAGE_OPTIMIZE_SQL);
    }

    private void maintain(ViewLogArchiveSource source, String optimizeSql) {
        var state = mapper.findState(source);
        var metrics = mapper.findMetrics(source);
        long deletedRows = state == null ? 0 : state.getDeletedRowsSinceOptimize();
        long freeBytes = metrics == null ? 0 : metrics.getDataFreeBytes();
        if (deletedRows < properties.optimizeMinDeletedRows()
                && freeBytes < properties.optimizeMinFreeBytes()) {
            log.debug("View log tablespace maintenance threshold not met: source={}, deletedRows={}, freeBytes={}",
                    source, deletedRows, freeBytes);
            return;
        }

        try {
            jdbcTemplate.update(optimizeSql);
            mapper.resetDeletedRows(source);
            log.info("View log tablespace optimized: source={}, deletedRows={}, freeBytes={}",
                    source, deletedRows, freeBytes);
        } catch (RuntimeException exception) {
            log.error("View log tablespace optimization failed: source={}", source, exception);
        }
    }

    private boolean callLockFunction(Connection connection, String sql, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }
}
