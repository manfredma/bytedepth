package manfred.bytedepth.domain.reading;

import java.time.Instant;

public record ReadingHistoryEntry(
        Long postId, String postSlug, String title, long readCount, long totalActiveSeconds, Instant lastReadAt) {}
