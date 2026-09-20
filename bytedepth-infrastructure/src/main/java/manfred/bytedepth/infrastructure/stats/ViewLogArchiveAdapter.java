package manfred.bytedepth.infrastructure.stats;

import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.app.analytics.ViewLogArchivePort;
import manfred.bytedepth.app.analytics.ViewLogArchiveResult;
import manfred.bytedepth.app.analytics.ViewLogArchiveRunResult;
import manfred.bytedepth.app.analytics.ViewLogArchiveSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
@Component
public class ViewLogArchiveAdapter implements ViewLogArchivePort {

    private static final String LOCK_NAME = "bytedepth:view-log-archive";
    private static final ViewLogArchiveRunResult EMPTY_RUN = new ViewLogArchiveRunResult(0, 0, 0);

    private final ViewLogArchiveMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final String lockName;

    public ViewLogArchiveAdapter(ViewLogArchiveMapper mapper, TransactionTemplate transactionTemplate) {
        this(mapper, transactionTemplate, null, LOCK_NAME);
    }

    public ViewLogArchiveAdapter(ViewLogArchiveMapper mapper,
                                 TransactionTemplate transactionTemplate,
                                 JdbcTemplate jdbcTemplate) {
        this(mapper, transactionTemplate, jdbcTemplate, LOCK_NAME);
    }

    @Autowired
    public ViewLogArchiveAdapter(ViewLogArchiveMapper mapper,
                                 TransactionTemplate transactionTemplate,
                                 JdbcTemplate jdbcTemplate,
                                 @Value("${bytedepth.analytics.archive-lock-name:bytedepth:view-log-archive}") String lockName) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.jdbcTemplate = jdbcTemplate;
        this.lockName = Objects.requireNonNull(lockName, "lockName");
    }

    @Override
    public List<LocalDateTime> findCandidateBuckets(ViewLogArchiveSource source,
                                                    LocalDateTime cutoff,
                                                    int maxBuckets) {
        return mapper.findCandidateBuckets(source, cutoff, maxBuckets);
    }

    @Override
    public ViewLogArchiveResult archiveBucket(ViewLogArchiveSource source,
                                              LocalDateTime bucketStart,
                                              LocalDateTime bucketEnd) {
        return transactionTemplate.execute(status -> archiveBucketInTransaction(source, bucketStart, bucketEnd));
    }

    public ViewLogArchiveRunResult runWithLock(Supplier<ViewLogArchiveRunResult> action) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate is required for named-lock execution");
        }
        return jdbcTemplate.execute((ConnectionCallback<ViewLogArchiveRunResult>) connection -> {
            if (!callLockFunction(connection, "SELECT GET_LOCK(?, 0)", lockName)) {
                return EMPTY_RUN;
            }
            try {
                return action.get();
            } finally {
                callLockFunction(connection, "SELECT RELEASE_LOCK(?)", lockName);
            }
        });
    }

    private ViewLogArchiveResult archiveBucketInTransaction(ViewLogArchiveSource source,
                                                            LocalDateTime bucketStart,
                                                            LocalDateTime bucketEnd) {
        var existingState = mapper.findBucketState(source, bucketStart);
        long rawRows = mapper.countRows(source, bucketStart, bucketEnd);
        if (rawRows == 0) {
            return new ViewLogArchiveResult(source, bucketStart, bucketEnd, 0, 0);
        }

        if (existingState == null) {
            mapper.insertExactAggregates(source, bucketStart, bucketEnd);
        } else {
            mapper.incrementAggregates(source, bucketStart, bucketEnd);
        }

        int deletedRows = mapper.deleteBucket(source, bucketStart, bucketEnd);
        if (deletedRows != rawRows) {
            throw new IllegalStateException("archive delete count does not match source row count");
        }

        if (existingState == null) {
            mapper.insertBucketState(source, bucketStart, deletedRows);
        } else {
            mapper.updateBucketState(source, bucketStart, deletedRows);
        }
        mapper.incrementTablespaceDeletedRows(source, deletedRows);
        return new ViewLogArchiveResult(source, bucketStart, bucketEnd, rawRows, deletedRows);
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
