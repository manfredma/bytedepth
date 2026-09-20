package manfred.bytedepth.app.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewLogArchiveResultTest {

    @Test
    void resultRecordsArchiveSourceRangeAndCounts() {
        var start = LocalDateTime.of(2026, 9, 11, 17, 0);
        var end = start.plusHours(1);
        var result = new ViewLogArchiveResult(ViewLogArchiveSource.POST, start, end, 4, 4);

        assertEquals(ViewLogArchiveSource.POST, result.source());
        assertEquals(start, result.bucketStart());
        assertEquals(end, result.bucketEnd());
        assertEquals(4, result.aggregatedRows());
        assertEquals(4, result.deletedRows());
    }

    @Test
    void runResultRecordsTotalsAcrossSources() {
        var result = new ViewLogArchiveRunResult(2, 9, 9);

        assertEquals(2, result.bucketCount());
        assertEquals(9, result.aggregatedRows());
        assertEquals(9, result.deletedRows());
    }
}
