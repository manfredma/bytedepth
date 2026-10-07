package manfred.bytedepth.app.reading;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.domain.reading.ReadingEventType;
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

    boolean inserted = new RecordReadingEventCmdExe(getPostQryExe, eventPort).execute(7L, "java", request());

    assertTrue(inserted);
    verify(eventPort).insertIfAbsent(org.mockito.ArgumentMatchers.argThat(event -> event.userId().equals(7L) && event.postId().equals(12L)));
  }

  @Test
  void rejectsUnpublishedPostBeforeWriting() {
    PostDTO post = new PostDTO();
    post.setId(12L);
    post.setStatus("DRAFT");
    when(getPostQryExe.executeBySlug("java")).thenReturn(post);

    assertThrows(IllegalArgumentException.class, () -> new RecordReadingEventCmdExe(getPostQryExe, eventPort).execute(7L, "java", request()));
  }

  private static RecordReadingEventCmdExe.ReadingEventRequest request() {
    return new RecordReadingEventCmdExe.ReadingEventRequest(UUID.randomUUID(), UUID.randomUUID(), ReadingEventType.READ_OPEN, 0, 0, Instant.now());
  }
}
