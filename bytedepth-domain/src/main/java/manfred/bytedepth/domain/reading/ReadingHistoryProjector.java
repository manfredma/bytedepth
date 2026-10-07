package manfred.bytedepth.domain.reading;

import java.time.Instant;

public final class ReadingHistoryProjector {

  private ReadingHistoryProjector() {}

  public static ReadingSummary apply(ReadingSummary current, ReadingEvent event) {
    if (current == null) {
      return new ReadingSummary(
          event.userId(),
          event.postId(),
          event.type() == ReadingEventType.READ_OPEN ? 1 : 0,
          event.activeSecondsDelta(),
          event.receivedAt(),
          event.receivedAt());
    }
    if (!current.userId().equals(event.userId()) || !current.postId().equals(event.postId())) {
      throw new IllegalArgumentException("event does not belong to the current reading summary");
    }
    Instant lastReadAt = current.lastReadAt().isAfter(event.receivedAt())
        ? current.lastReadAt()
        : event.receivedAt();
    return new ReadingSummary(
        current.userId(),
        current.postId(),
        current.readCount() + (event.type() == ReadingEventType.READ_OPEN ? 1 : 0),
        current.totalActiveSeconds() + event.activeSecondsDelta(),
        current.firstReadAt(),
        lastReadAt);
  }
}
