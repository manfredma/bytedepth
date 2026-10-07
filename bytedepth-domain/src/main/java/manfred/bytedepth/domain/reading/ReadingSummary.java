package manfred.bytedepth.domain.reading;

import java.time.Instant;
import java.util.Objects;

public record ReadingSummary(
    Long userId,
    Long postId,
    long readCount,
    long totalActiveSeconds,
    Instant firstReadAt,
    Instant lastReadAt) {

  public ReadingSummary {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(postId, "postId");
    Objects.requireNonNull(firstReadAt, "firstReadAt");
    Objects.requireNonNull(lastReadAt, "lastReadAt");
    if (readCount < 0 || totalActiveSeconds < 0) {
      throw new IllegalArgumentException("reading summary values cannot be negative");
    }
  }
}
