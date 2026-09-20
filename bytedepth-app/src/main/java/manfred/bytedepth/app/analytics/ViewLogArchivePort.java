package manfred.bytedepth.app.analytics;

import java.time.LocalDateTime;
import java.util.List;

public interface ViewLogArchivePort {

    List<LocalDateTime> findCandidateBuckets(ViewLogArchiveSource source,
                                             LocalDateTime cutoff,
                                             int maxBuckets);

    ViewLogArchiveResult archiveBucket(ViewLogArchiveSource source,
                                       LocalDateTime bucketStart,
                                       LocalDateTime bucketEnd);
}
