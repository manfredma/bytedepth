package manfred.bytedepth.app.analytics;

import java.time.LocalDateTime;

public record ViewLogArchiveResult(
        ViewLogArchiveSource source,
        LocalDateTime bucketStart,
        LocalDateTime bucketEnd,
        long aggregatedRows,
        long deletedRows) {
}
