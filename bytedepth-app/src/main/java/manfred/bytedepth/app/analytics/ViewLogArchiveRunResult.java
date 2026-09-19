package manfred.bytedepth.app.analytics;

public record ViewLogArchiveRunResult(
        int bucketCount,
        long aggregatedRows,
        long deletedRows) {
}
