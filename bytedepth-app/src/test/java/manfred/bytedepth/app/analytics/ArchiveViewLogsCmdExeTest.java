package manfred.bytedepth.app.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchiveViewLogsCmdExeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 17, 23);
    private static final ViewLogRetentionPolicy POLICY =
            new ViewLogRetentionPolicy(7, ZoneId.of("Asia/Shanghai"));

    @Test
    void archiveReturnsEmptyTotalsWhenBothSourcesHaveNoCandidates() {
        var port = mock(ViewLogArchivePort.class);
        when(port.findCandidateBuckets(any(), any(), eq(24))).thenReturn(List.of());

        var result = new ArchiveViewLogsCmdExe(port, POLICY).archive(NOW, 24);

        assertEquals(new ViewLogArchiveRunResult(0, 0, 0), result);
        verify(port).findCandidateBuckets(ViewLogArchiveSource.POST,
                LocalDateTime.of(2026, 9, 11, 17, 0), 24);
        verify(port).findCandidateBuckets(ViewLogArchiveSource.PAGE,
                LocalDateTime.of(2026, 9, 11, 17, 0), 24);
    }

    @Test
    void archiveProcessesOneCandidateAndAggregatesItsCounts() {
        var port = mock(ViewLogArchivePort.class);
        var start = LocalDateTime.of(2026, 9, 1, 10, 0);
        var end = start.plusHours(1);
        when(port.findCandidateBuckets(eq(ViewLogArchiveSource.POST), any(), eq(1))).thenReturn(List.of(start));
        when(port.findCandidateBuckets(eq(ViewLogArchiveSource.PAGE), any(), eq(1))).thenReturn(List.of());
        when(port.archiveBucket(ViewLogArchiveSource.POST, start, end))
                .thenReturn(new ViewLogArchiveResult(ViewLogArchiveSource.POST, start, end, 3, 3));

        var result = new ArchiveViewLogsCmdExe(port, POLICY).archive(NOW, 1);

        assertEquals(new ViewLogArchiveRunResult(1, 3, 3), result);
        verify(port).archiveBucket(ViewLogArchiveSource.POST, start, end);
    }

    @Test
    void archiveProcessesAtMostTheConfiguredBucketLimitPerSource() {
        var port = mock(ViewLogArchivePort.class);
        var first = LocalDateTime.of(2026, 9, 1, 10, 0);
        var second = first.plusHours(1);
        when(port.findCandidateBuckets(ViewLogArchiveSource.POST,
                LocalDateTime.of(2026, 9, 11, 17, 0), 1)).thenReturn(List.of(first, second));
        when(port.findCandidateBuckets(ViewLogArchiveSource.PAGE,
                LocalDateTime.of(2026, 9, 11, 17, 0), 1)).thenReturn(List.of());
        when(port.archiveBucket(ViewLogArchiveSource.POST, first, second))
                .thenReturn(new ViewLogArchiveResult(ViewLogArchiveSource.POST, first, second, 3, 3));

        var result = new ArchiveViewLogsCmdExe(port, POLICY).archive(NOW, 1);

        assertEquals(new ViewLogArchiveRunResult(1, 3, 3), result);
        verify(port).archiveBucket(ViewLogArchiveSource.POST, first, second);
        verify(port, never()).archiveBucket(ViewLogArchiveSource.POST, second, second.plusHours(1));
    }

    @Test
    void archivePropagatesAdapterFailureWithoutStartingTheNextSource() {
        var port = mock(ViewLogArchivePort.class);
        var start = LocalDateTime.of(2026, 9, 1, 10, 0);
        when(port.findCandidateBuckets(ViewLogArchiveSource.POST,
                LocalDateTime.of(2026, 9, 11, 17, 0), 1)).thenReturn(List.of(start));
        when(port.archiveBucket(ViewLogArchiveSource.POST, start, start.plusHours(1)))
                .thenThrow(new IllegalStateException("archive failed"));

        assertThrows(IllegalStateException.class, () -> new ArchiveViewLogsCmdExe(port, POLICY).archive(NOW, 1));
        verify(port, never()).findCandidateBuckets(eq(ViewLogArchiveSource.PAGE), any(), eq(1));
    }

    @Test
    void archiveRejectsNonPositiveBucketLimit() {
        var port = mock(ViewLogArchivePort.class);

        assertThrows(IllegalArgumentException.class, () -> new ArchiveViewLogsCmdExe(port, POLICY).archive(NOW, 0));
    }
}
