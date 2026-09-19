package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.app.analytics.ViewLogArchiveResult;
import manfred.bytedepth.app.analytics.ViewLogArchiveRunResult;
import manfred.bytedepth.app.analytics.ViewLogArchiveSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewLogArchiveAdapterTest {

    private static final LocalDateTime BUCKET = LocalDateTime.of(2026, 9, 11, 17, 0);

    @Test
    void archiveBucketUsesExactPathForANewBucketAndUpdatesMaintenanceState() {
        var mapper = mock(ViewLogArchiveMapper.class);
        when(mapper.findBucketState(ViewLogArchiveSource.POST, BUCKET)).thenReturn(null);
        when(mapper.countRows(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1))).thenReturn(2L);
        when(mapper.deleteBucket(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1))).thenReturn(2);

        var result = new ViewLogArchiveAdapter(mapper, synchronousTransactionTemplate()).archiveBucket(
                ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1));

        assertEquals(new ViewLogArchiveResult(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1), 2, 2), result);
        var order = inOrder(mapper);
        order.verify(mapper).insertExactAggregates(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1));
        order.verify(mapper).deleteBucket(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1));
        order.verify(mapper).insertBucketState(ViewLogArchiveSource.POST, BUCKET, 2);
        order.verify(mapper).incrementTablespaceDeletedRows(ViewLogArchiveSource.POST, 2);
    }

    @Test
    void delegatesCandidateBucketLookupToTheMapper() {
        var mapper = mock(ViewLogArchiveMapper.class);
        when(mapper.findCandidateBuckets(ViewLogArchiveSource.PAGE, BUCKET, 2)).thenReturn(List.of(BUCKET));
        var adapter = new ViewLogArchiveAdapter(mapper, synchronousTransactionTemplate());

        assertEquals(List.of(BUCKET), adapter.findCandidateBuckets(ViewLogArchiveSource.PAGE, BUCKET, 2));
    }

    @Test
    void threeArgumentConstructorUsesTheDefaultLockName() throws Exception {
        var jdbcTemplate = lockJdbcTemplate(0);
        var adapter = new ViewLogArchiveAdapter(mock(ViewLogArchiveMapper.class),
                synchronousTransactionTemplate(), jdbcTemplate);

        assertEquals(new ViewLogArchiveRunResult(0, 0, 0), adapter.runWithLock(() -> new ViewLogArchiveRunResult(1, 1, 1)));
    }

    @Test
    void archiveBucketUsesIncrementPathForAPreviouslyArchivedBucket() {
        var mapper = mock(ViewLogArchiveMapper.class);
        when(mapper.findBucketState(ViewLogArchiveSource.POST, BUCKET)).thenReturn(
                new ViewLogArchiveBucket(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1), 12));
        when(mapper.countRows(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1))).thenReturn(2L);
        when(mapper.deleteBucket(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1))).thenReturn(2);

        var result = new ViewLogArchiveAdapter(mapper, synchronousTransactionTemplate()).archiveBucket(
                ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1));

        assertEquals(2, result.deletedRows());
        verify(mapper).incrementAggregates(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1));
        verify(mapper).updateBucketState(ViewLogArchiveSource.POST, BUCKET, 2);
        verify(mapper).incrementTablespaceDeletedRows(ViewLogArchiveSource.POST, 2);
        verify(mapper, never()).insertExactAggregates(any(), any(), any());
    }

    @Test
    void archiveBucketDoesNothingWhenCandidateHasNoRows() {
        var mapper = mock(ViewLogArchiveMapper.class);
        when(mapper.findBucketState(ViewLogArchiveSource.PAGE, BUCKET)).thenReturn(null);
        when(mapper.countRows(ViewLogArchiveSource.PAGE, BUCKET, BUCKET.plusHours(1))).thenReturn(0L);

        var result = new ViewLogArchiveAdapter(mapper, synchronousTransactionTemplate()).archiveBucket(
                ViewLogArchiveSource.PAGE, BUCKET, BUCKET.plusHours(1));

        assertEquals(new ViewLogArchiveResult(ViewLogArchiveSource.PAGE, BUCKET, BUCKET.plusHours(1), 0, 0), result);
        verify(mapper, never()).insertExactAggregates(any(), any(), any());
        verify(mapper, never()).deleteBucket(any(), any(), any());
        verify(mapper, never()).insertBucketState(any(), any(), anyLong());
    }

    @Test
    void archiveBucketRethrowsFailureAndMarksTheTransactionRollbackOnly() {
        var mapper = mock(ViewLogArchiveMapper.class);
        when(mapper.findBucketState(ViewLogArchiveSource.POST, BUCKET)).thenReturn(null);
        when(mapper.countRows(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1))).thenReturn(2L);
        when(mapper.insertExactAggregates(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1)))
                .thenThrow(new IllegalStateException("aggregate failed"));
        var transactionStatus = mock(TransactionStatus.class);

        var transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            try {
                return ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(transactionStatus);
            } catch (RuntimeException exception) {
                transactionStatus.setRollbackOnly();
                throw exception;
            }
        });

        assertThrows(IllegalStateException.class, () -> new ViewLogArchiveAdapter(mapper, transactionTemplate)
                .archiveBucket(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1)));
        verify(transactionStatus).setRollbackOnly();
        verify(mapper, never()).deleteBucket(any(), any(), any());
    }

    @Test
    void archiveBucketRejectsADeleteCountMismatchBeforeWritingState() {
        var mapper = mock(ViewLogArchiveMapper.class);
        when(mapper.findBucketState(ViewLogArchiveSource.POST, BUCKET)).thenReturn(null);
        when(mapper.countRows(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1))).thenReturn(2L);
        when(mapper.deleteBucket(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1))).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> new ViewLogArchiveAdapter(mapper, synchronousTransactionTemplate())
                .archiveBucket(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1)));
        verify(mapper, never()).insertBucketState(any(), any(), anyLong());
        verify(mapper, never()).incrementTablespaceDeletedRows(any(), anyLong());
    }

    @Test
    void runWithLockReturnsEmptyResultWhenNamedLockIsUnavailable() throws Exception {
        var jdbcTemplate = lockJdbcTemplate(0);
        var mapper = mock(ViewLogArchiveMapper.class);
        var adapter = new ViewLogArchiveAdapter(mapper, synchronousTransactionTemplate(), jdbcTemplate, "bytedepth:view-log-archive");

        var result = adapter.runWithLock(() -> new ViewLogArchiveRunResult(1, 1, 1));

        assertEquals(new ViewLogArchiveRunResult(0, 0, 0), result);
        verify(mapper, never()).findBucketState(any(), any());
    }

    @Test
    void runWithLockExecutesActionAndReleasesNamedLockOnTheSameConnection() throws Exception {
        var jdbcTemplate = lockJdbcTemplate(1);
        var adapter = new ViewLogArchiveAdapter(mock(ViewLogArchiveMapper.class),
                synchronousTransactionTemplate(), jdbcTemplate, "bytedepth:view-log-archive");

        var result = adapter.runWithLock(() -> new ViewLogArchiveRunResult(2, 4, 4));

        assertEquals(new ViewLogArchiveRunResult(2, 4, 4), result);
    }

    @Test
    void runWithLockReturnsEmptyResultWhenLockFunctionReturnsNoRow() throws Exception {
        var jdbcTemplate = lockJdbcTemplateWithoutResultRow();
        var adapter = new ViewLogArchiveAdapter(mock(ViewLogArchiveMapper.class),
                synchronousTransactionTemplate(), jdbcTemplate, "bytedepth:view-log-archive");

        assertEquals(new ViewLogArchiveRunResult(0, 0, 0), adapter.runWithLock(() -> new ViewLogArchiveRunResult(1, 1, 1)));
    }

    @Test
    void runWithLockRequiresJdbcTemplate() {
        var adapter = new ViewLogArchiveAdapter(mock(ViewLogArchiveMapper.class), synchronousTransactionTemplate());

        assertThrows(IllegalStateException.class, () -> adapter.runWithLock(() -> new ViewLogArchiveRunResult(1, 1, 1)));
    }

    private static TransactionTemplate synchronousTransactionTemplate() {
        var transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        return transactionTemplate;
    }

    @SuppressWarnings("unchecked")
    private static JdbcTemplate lockJdbcTemplate(int lockResult) throws Exception {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var connection = mock(Connection.class);
        var lockStatement = mock(PreparedStatement.class);
        var lockRows = mock(ResultSet.class);
        var releaseStatement = mock(PreparedStatement.class);
        var releaseRows = mock(ResultSet.class);
        when(lockRows.next()).thenReturn(true);
        when(lockRows.getInt(1)).thenReturn(lockResult);
        when(releaseRows.next()).thenReturn(true);
        when(releaseRows.getInt(1)).thenReturn(1);
        when(lockStatement.executeQuery()).thenReturn(lockRows);
        when(releaseStatement.executeQuery()).thenReturn(releaseRows);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(lockStatement);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(releaseStatement);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation ->
                ((ConnectionCallback<Object>) invocation.getArgument(0)).doInConnection(connection));
        return jdbcTemplate;
    }

    @SuppressWarnings("unchecked")
    private static JdbcTemplate lockJdbcTemplateWithoutResultRow() throws Exception {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var connection = mock(Connection.class);
        var lockStatement = mock(PreparedStatement.class);
        var lockRows = mock(ResultSet.class);
        when(lockRows.next()).thenReturn(false);
        when(lockStatement.executeQuery()).thenReturn(lockRows);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(lockStatement);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation ->
                ((ConnectionCallback<Object>) invocation.getArgument(0)).doInConnection(connection));
        return jdbcTemplate;
    }
}
