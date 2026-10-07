package manfred.bytedepth.app.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.domain.reading.ReadingEventType;
import manfred.bytedepth.domain.reading.ReadingHistoryEntry;
import manfred.bytedepth.domain.reading.ReadingSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordReadingEventCmdExeTest {

  @Mock private GetPostQryExe getPostQryExe;
  @Mock private ReadingEventPort eventPort;

  @Test
  void acceptsPublishedPostAndKeepsTheAuthenticatedUserId() {
    PostDTO post = new PostDTO();
    post.setId(12L);
    post.setStatus("PUBLISHED");
    when(getPostQryExe.executeBySlug("java")).thenReturn(post);
    when(eventPort.insertIfAbsent(org.mockito.ArgumentMatchers.any())).thenReturn(true);

    boolean inserted =
        new RecordReadingEventCmdExe(getPostQryExe, eventPort).execute(7L, "java", request());

    assertTrue(inserted);
    verify(eventPort)
        .insertIfAbsent(
            org.mockito.ArgumentMatchers.argThat(
                event -> event.userId().equals(7L) && event.postId().equals(12L)));
  }

  @Test
  void rejectsUnpublishedPostBeforeWriting() {
    PostDTO post = new PostDTO();
    post.setId(12L);
    post.setStatus("DRAFT");
    when(getPostQryExe.executeBySlug("java")).thenReturn(post);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RecordReadingEventCmdExe(getPostQryExe, eventPort).execute(7L, "java", request()));
  }

  @Test
  void rejectsInvalidPayloadAndExcessiveHeartbeatDelta() {
    PostDTO post = new PostDTO();
    post.setId(12L);
    post.setStatus("PUBLISHED");
    when(getPostQryExe.executeBySlug("java")).thenReturn(post);
    var command = new RecordReadingEventCmdExe(getPostQryExe, eventPort);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            command.execute(
                7L,
                "java",
                new RecordReadingEventCmdExe.ReadingEventRequest(null, null, null, -1, 101, null)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            command.execute(
                7L,
                "java",
                new RecordReadingEventCmdExe.ReadingEventRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ReadingEventType.READ_HEARTBEAT,
                    61,
                    0,
                    Instant.now())));
    assertFalse(
        new RecordReadingEventCmdExe.ReadingEventRequest(
                null, UUID.randomUUID(), ReadingEventType.READ_OPEN, 0, 0, null)
            .valid());
    UUID eventId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    assertFalse(
        new RecordReadingEventCmdExe.ReadingEventRequest(
                eventId, null, ReadingEventType.READ_OPEN, 0, 0, null)
            .valid());
    assertFalse(
        new RecordReadingEventCmdExe.ReadingEventRequest(eventId, sessionId, null, 0, 0, null)
            .valid());
    assertFalse(
        new RecordReadingEventCmdExe.ReadingEventRequest(
                eventId, sessionId, ReadingEventType.READ_OPEN, -1, 0, null)
            .valid());
    assertFalse(
        new RecordReadingEventCmdExe.ReadingEventRequest(
                eventId, sessionId, ReadingEventType.READ_OPEN, 0, -1, null)
            .valid());
    assertFalse(
        new RecordReadingEventCmdExe.ReadingEventRequest(
                eventId, sessionId, ReadingEventType.READ_OPEN, 0, 101, null)
            .valid());
  }

  @Test
  void queryExecutorsAndPendingEventExposeTheirPortValues() {
    ReadingSummary summary = new ReadingSummary(7L, 12L, 1, 15, Instant.now(), Instant.now());
    var history = org.mockito.Mockito.mock(ReadingHistoryPort.class);
    when(history.findByUserAndPost(7L, 12L)).thenReturn(summary);
    assertEquals(summary, new GetReadingSummaryQryExe(history).execute(7L, 12L));
    var entry = new ReadingHistoryEntry(12L, "java", "Java", 1, 15, Instant.now());
    when(history.findPageByUser(7L, "cursor", 20)).thenReturn(java.util.List.of(entry));
    assertEquals(
        java.util.List.of(entry), new ListReadingHistoryQryExe(history).execute(7L, "cursor"));
    var event =
        new manfred.bytedepth.domain.reading.ReadingEvent(
            UUID.randomUUID(),
            7L,
            12L,
            UUID.randomUUID(),
            ReadingEventType.READ_OPEN,
            0,
            0,
            null,
            Instant.now());
    assertEquals(9L, new PendingReadingEvent(9L, event).rowId());
  }

  private static RecordReadingEventCmdExe.ReadingEventRequest request() {
    return new RecordReadingEventCmdExe.ReadingEventRequest(
        UUID.randomUUID(), UUID.randomUUID(), ReadingEventType.READ_OPEN, 0, 0, Instant.now());
  }
}
