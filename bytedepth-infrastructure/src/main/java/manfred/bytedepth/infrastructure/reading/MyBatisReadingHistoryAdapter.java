package manfred.bytedepth.infrastructure.reading;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.reading.PendingReadingEvent;
import manfred.bytedepth.app.reading.ReadingEventPort;
import manfred.bytedepth.app.reading.ReadingHistoryPort;
import manfred.bytedepth.domain.reading.ReadingEvent;
import manfred.bytedepth.domain.reading.ReadingEventType;
import manfred.bytedepth.domain.reading.ReadingHistoryEntry;
import manfred.bytedepth.domain.reading.ReadingSummary;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MyBatisReadingHistoryAdapter implements ReadingEventPort, ReadingHistoryPort {

  private static final ZoneId ZONE = ZoneId.systemDefault();
  private final ReadingHistoryMapper mapper;

  @Override
  public boolean insertIfAbsent(ReadingEvent event) {
    return mapper.insertIfAbsent(toDO(event)) > 0;
  }

  @Override
  public List<PendingReadingEvent> findUnprojected(int limit) {
    return mapper.findUnprojected(limit).stream()
        .map(row -> new PendingReadingEvent(row.getId(), fromDO(row)))
        .toList();
  }

  @Override
  public void markProjected(long eventRowId, Instant projectedAt) {
    mapper.markProjected(eventRowId, local(projectedAt));
  }

  @Override
  public int deleteProjectedBefore(Instant cutoff) {
    return mapper.deleteProjectedBefore(local(cutoff));
  }

  @Override
  public void upsert(ReadingEvent event) {
    mapper.upsertHistory(toDO(event));
  }

  @Override
  public ReadingSummary findByUserAndPost(long userId, long postId) {
    ReadingHistoryDO row = mapper.findSummary(userId, postId);
    return row == null ? null : summary(row);
  }

  @Override
  public List<ReadingHistoryEntry> findPageByUser(long userId, String cursor, int limit) {
    LocalDateTime cursorTime = null;
    Long cursorPostId = null;
    if (cursor != null && !cursor.isBlank()) {
      String[] parts = cursor.split(":", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("invalid reading history cursor");
      }
      cursorTime = LocalDateTime.ofInstant(Instant.parse(parts[0]), ZONE);
      cursorPostId = Long.valueOf(parts[1]);
    }
    return mapper.findPage(userId, cursorTime, cursorPostId, limit).stream()
        .map(
            row ->
                new ReadingHistoryEntry(
                    row.getPostId(),
                    row.getPostSlug(),
                    row.getTitle(),
                    row.getReadCount(),
                    row.getTotalActiveSeconds(),
                    instant(row.getLastReadAt())))
        .toList();
  }

  private static ReadingEventDO toDO(ReadingEvent event) {
    var row = new ReadingEventDO();
    row.setEventId(event.eventId());
    row.setUserId(event.userId());
    row.setPostId(event.postId());
    row.setSessionId(event.sessionId());
    row.setEventType(event.type().name());
    row.setActiveSecondsDelta(event.activeSecondsDelta());
    row.setMaxScrollDepth(event.maxScrollDepth());
    row.setOccurredAt(event.occurredAt() == null ? null : local(event.occurredAt()));
    row.setReceivedAt(local(event.receivedAt()));
    return row;
  }

  private static ReadingEvent fromDO(ReadingEventDO row) {
    return new ReadingEvent(
        row.getEventId(),
        row.getUserId(),
        row.getPostId(),
        row.getSessionId(),
        ReadingEventType.valueOf(row.getEventType()),
        row.getActiveSecondsDelta(),
        row.getMaxScrollDepth(),
        row.getOccurredAt() == null ? null : instant(row.getOccurredAt()),
        instant(row.getReceivedAt()));
  }

  private static ReadingSummary summary(ReadingHistoryDO row) {
    return new ReadingSummary(
        row.getUserId(),
        row.getPostId(),
        row.getReadCount(),
        row.getTotalActiveSeconds(),
        instant(row.getFirstReadAt()),
        instant(row.getLastReadAt()));
  }

  private static LocalDateTime local(Instant instant) {
    return LocalDateTime.ofInstant(instant, ZONE);
  }

  private static Instant instant(LocalDateTime local) {
    return local.atZone(ZONE).toInstant();
  }
}
