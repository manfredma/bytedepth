package manfred.bytedepth.app.analytics;

import java.time.LocalDateTime;
import java.util.Objects;

public final class ArchiveViewLogsCmdExe {

    private final ViewLogArchivePort archivePort;
    private final ViewLogRetentionPolicy retentionPolicy;

    public ArchiveViewLogsCmdExe(ViewLogArchivePort archivePort, ViewLogRetentionPolicy retentionPolicy) {
        this.archivePort = Objects.requireNonNull(archivePort, "archivePort");
        this.retentionPolicy = Objects.requireNonNull(retentionPolicy, "retentionPolicy");
    }

    public ViewLogArchiveRunResult archive(LocalDateTime now, int maxBucketsPerSource) {
        if (maxBucketsPerSource <= 0) {
            throw new IllegalArgumentException("maxBucketsPerSource must be positive");
        }

        var cutoff = retentionPolicy.retentionCutoff(now);
        int bucketCount = 0;
        long aggregatedRows = 0;
        long deletedRows = 0;

        for (var source : ViewLogArchiveSource.values()) {
            var candidates = archivePort.findCandidateBuckets(source, cutoff, maxBucketsPerSource);
            int candidateCount = Math.min(maxBucketsPerSource, candidates.size());
            for (int index = 0; index < candidateCount; index++) {
                var bucketStart = candidates.get(index);
                var result = archivePort.archiveBucket(source, bucketStart, bucketStart.plusHours(1));
                bucketCount++;
                aggregatedRows += result.aggregatedRows();
                deletedRows += result.deletedRows();
            }
        }

        return new ViewLogArchiveRunResult(bucketCount, aggregatedRows, deletedRows);
    }
}
