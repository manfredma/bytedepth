package manfred.bytedepth.adapter.web.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import manfred.bytedepth.adapter.web.security.SiteUserDetails;
import manfred.bytedepth.app.reading.ListReadingHistoryQryExe;
import manfred.bytedepth.app.reading.RecordReadingEventCmdExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.reading.ReadingEventType;
import manfred.bytedepth.domain.reading.ReadingHistoryEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;

class ReadingControllersCoverageTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readingEventControllerCoversUnauthenticatedAcceptedAndInvalidPaths() {
        var command = mock(RecordReadingEventCmdExe.class);
        var controller = new ReadingEventController(command);
        var request = request();

        assertThat(controller.record("java", request).getStatusCode().value()).isEqualTo(401);

        authenticate();
        assertThat(controller.record("java", request).getStatusCode().value()).isEqualTo(202);

        when(command.execute(any(Long.class), any(String.class), any())).thenThrow(IllegalArgumentException.class);
        assertThat(controller.record("java", request).getStatusCode().value()).isEqualTo(400);
        assertThat(new ReadingEventController.RecordReadingEventRequest(null, null, null, 0, 0, null).eventId())
                .isNull();
    }

    @Test
    void readingHistoryControllerCoversAnonymousAuthenticatedAvailabilityAndLimit() {
        var list = mock(ListReadingHistoryQryExe.class);
        var posts = mock(PostRepository.class);
        var controller = new ReadingHistoryController(list, posts);
        Model model = mock(Model.class);
        controller.page(model, null);

        authenticate();
        var entry = new ReadingHistoryEntry(12L, "java", "Java", 2, 30, Instant.parse("2026-10-07T10:00:00Z"));
        when(list.execute(7L, null))
                .thenReturn(
                        java.util.stream.Stream.generate(() -> entry).limit(20).toList());
        controller.page(model, null);

        Post published = mock(Post.class);
        when(published.getStatus()).thenReturn(PostStatus.PUBLISHED);
        when(published.getSlug()).thenReturn("java");
        when(published.getTitle()).thenReturn("Java");
        when(posts.findBySlug("java")).thenReturn(java.util.Optional.of(published));
        Post draft = mock(Post.class);
        when(draft.getStatus()).thenReturn(PostStatus.DRAFT);
        when(posts.findBySlug("draft")).thenReturn(java.util.Optional.of(draft));
        assertThat(controller.availablePosts(List.of("java", "draft", "missing")))
                .hasSize(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.availablePosts(java.util.stream.IntStream.range(0, 21)
                        .mapToObj(i -> "x" + i)
                        .toList()));
        assertThat(new ReadingHistoryController.AvailablePost("java", "Java", "/posts/java").path())
                .isEqualTo("/posts/java");
    }

    private static ReadingEventController.RecordReadingEventRequest request() {
        return new ReadingEventController.RecordReadingEventRequest(
                UUID.randomUUID(), UUID.randomUUID(), ReadingEventType.READ_OPEN, 0, 0, Instant.now());
    }

    private static void authenticate() {
        var principal = new SiteUserDetails(7L, "reader", "", List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
