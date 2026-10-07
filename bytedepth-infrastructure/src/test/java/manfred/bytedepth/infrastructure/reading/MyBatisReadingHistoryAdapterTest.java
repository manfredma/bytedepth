package manfred.bytedepth.infrastructure.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import manfred.bytedepth.domain.reading.ReadingEvent;
import manfred.bytedepth.domain.reading.ReadingEventType;
import org.junit.jupiter.api.Test;

class MyBatisReadingHistoryAdapterTest {

  private static final Instant RECEIVED_AT = Instant.parse("2026-10-07T10:00:00Z");

  private final ReadingHistoryMapper mapper = mock(ReadingHistoryMapper.class);
  private final MyBatisReadingHistoryAdapter adapter = new MyBatisReadingHistoryAdapter(mapper);

  @Test
  void writesEventsAndMapsPendingRows() {
    ReadingEvent event = event(ReadingEventType.READ_OPEN, RECEIVED_AT, RECEIVED_AT);
    var row = eventRow(event, 41L, RECEIVED_AT);
    when(mapper.insertIfAbsent(any())).thenReturn(1);
    when(mapper.findUnprojected(10)).thenReturn(List.of(row));

    assertThat(adapter.insertIfAbsent(event)).isTrue();
    var pending = adapter.findUnprojected(10);

    assertThat(pending).hasSize(1);
    assertThat(pending.getFirst().rowId()).isEqualTo(41L);
    assertThat(pending.getFirst().event()).isEqualTo(event);
    verify(mapper).insertIfAbsent(any(ReadingEventDO.class));
  }

  @Test
  void returnsFalseWhenInsertIsDuplicateAndAcceptsEventsWithoutOccurredAt() {
    ReadingEvent event = event(ReadingEventType.READ_HEARTBEAT, null, RECEIVED_AT);
    when(mapper.insertIfAbsent(any())).thenReturn(0);

    assertThat(adapter.insertIfAbsent(event)).isFalse();
  }

  @Test
  void mapsNullableOccurredAtAndSummaryRows() {
    ReadingEvent event = event(ReadingEventType.READ_HEARTBEAT, null, RECEIVED_AT);
    var row = eventRow(event, 42L, null);
    when(mapper.findUnprojected(anyInt())).thenReturn(List.of(row));
    var history = historyRow();
    when(mapper.findSummary(7L, 12L)).thenReturn(history);

    assertThat(adapter.findUnprojected(100).getFirst().event().occurredAt()).isNull();
    var summary = adapter.findByUserAndPost(7L, 12L);

    assertThat(summary.readCount()).isEqualTo(3);
    assertThat(summary.totalActiveSeconds()).isEqualTo(90);
    assertThat(summary.firstReadAt()).isEqualTo(RECEIVED_AT);
    assertThat(summary.lastReadAt()).isEqualTo(RECEIVED_AT.plusSeconds(2));
  }

  @Test
  void mapsMissingSummaryAndPageCursors() {
    when(mapper.findSummary(7L, 12L)).thenReturn(null);
    when(mapper.findPage(7L, null, null, 20)).thenReturn(List.of(historyRow()));
    when(mapper.findPage(
            7L, LocalDateTime.ofInstant(RECEIVED_AT, java.time.ZoneId.systemDefault()), 12L, 20))
        .thenReturn(List.of(historyRow()));

    assertThat(adapter.findByUserAndPost(7L, 12L)).isNull();
    assertThat(adapter.findPageByUser(7L, "", 20)).hasSize(1);
    assertThat(adapter.findPageByUser(7L, RECEIVED_AT + "|12", 20)).hasSize(1);
    assertThat(adapter.findPageByUser(7L, null, 20).getFirst().postSlug()).isEqualTo("post-slug");
  }

  @Test
  void rejectsMalformedCursorsAndDelegatesMutations() {
    ReadingEvent event = event(ReadingEventType.READ_CLOSE, RECEIVED_AT, RECEIVED_AT);

    assertThatThrownBy(() -> adapter.findPageByUser(7L, "bad", 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.findPageByUser(7L, "not-an-instant|12", 20))
        .isInstanceOf(RuntimeException.class);

    adapter.markProjected(41L, RECEIVED_AT);
    adapter.deleteProjectedBefore(RECEIVED_AT);
    adapter.upsert(event);
    verify(mapper).markProjected(anyLong(), any(LocalDateTime.class));
    verify(mapper).deleteProjectedBefore(any(LocalDateTime.class));
    verify(mapper).upsertHistory(any(ReadingEventDO.class));
  }

  private static ReadingEvent event(ReadingEventType type, Instant occurredAt, Instant receivedAt) {
    return new ReadingEvent(
        UUID.randomUUID(), 7L, 12L, UUID.randomUUID(), type, 3, 80, occurredAt, receivedAt);
  }

  private static ReadingEventDO eventRow(ReadingEvent event, long id, Instant occurredAt) {
    var row = new ReadingEventDO();
    row.setId(id);
    row.setEventId(event.eventId());
    row.setUserId(event.userId());
    row.setPostId(event.postId());
    row.setSessionId(event.sessionId());
    row.setEventType(event.type().name());
    row.setActiveSecondsDelta(event.activeSecondsDelta());
    row.setMaxScrollDepth(event.maxScrollDepth());
    row.setOccurredAt(
        occurredAt == null
            ? null
            : LocalDateTime.ofInstant(occurredAt, java.time.ZoneId.systemDefault()));
    row.setReceivedAt(
        LocalDateTime.ofInstant(event.receivedAt(), java.time.ZoneId.systemDefault()));
    return row;
  }

  private static ReadingHistoryDO historyRow() {
    var row = new ReadingHistoryDO();
    row.setUserId(7L);
    row.setPostId(12L);
    row.setReadCount(3L);
    row.setTotalActiveSeconds(90L);
    row.setFirstReadAt(LocalDateTime.ofInstant(RECEIVED_AT, java.time.ZoneId.systemDefault()));
    row.setLastReadAt(
        LocalDateTime.ofInstant(RECEIVED_AT.plusSeconds(2), java.time.ZoneId.systemDefault()));
    row.setPostSlug("post-slug");
    row.setTitle("Post title");
    return row;
  }
}
