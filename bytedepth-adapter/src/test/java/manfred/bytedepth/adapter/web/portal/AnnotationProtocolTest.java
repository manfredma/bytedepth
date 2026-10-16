package manfred.bytedepth.adapter.web.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import manfred.bytedepth.adapter.web.security.SiteUserDetails;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.app.annotation.*;
import manfred.bytedepth.domain.post.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AnnotationProtocolTest {
    final PostRepository posts = mock(PostRepository.class);
    final ListAnnotationsQryExe list = mock(ListAnnotationsQryExe.class);
    final CreateAnnotationCmdExe create = mock(CreateAnnotationCmdExe.class);
    final DeleteAnnotationCmdExe delete = mock(DeleteAnnotationCmdExe.class);
    final UpdateAnnotationCmdExe update = mock(UpdateAnnotationCmdExe.class);
    final AnnotationController controller = new AnnotationController(
            posts, list, create, delete, update, mock(AnnotationVisitorIdentity.class), new MarkdownRenderer());

    @Test
    void U39_draftAndForeignRevisionStayPrivateOnEveryRoute() {
        var draft = Post.reconstruct(
                1L,
                "slug",
                "title",
                "body",
                PostStatus.DRAFT,
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                null,
                7L,
                false);
        when(posts.findBySlug("slug")).thenReturn(Optional.of(draft));
        for (var viewer : new org.springframework.security.core.userdetails.UserDetails[] {
            null, new SiteUserDetails(8L, "other", "", List.of())
        }) {
            assertThrows(
                    java.util.NoSuchElementException.class,
                    () -> controller.list("slug", viewer, new MockHttpServletRequest()));
            assertThrows(
                    java.util.NoSuchElementException.class,
                    () -> controller.create(
                            "slug", null, viewer, new MockHttpServletRequest(), new MockHttpServletResponse()));
            assertThrows(
                    java.util.NoSuchElementException.class,
                    () -> controller.update("slug", 9L, null, viewer, new MockHttpServletRequest()));
            assertThrows(
                    java.util.NoSuchElementException.class,
                    () -> controller.delete("slug", 9L, viewer, new MockHttpServletRequest()));
        }
        verifyNoInteractions(list, create, update, delete);
        var owner = new SiteUserDetails(7L, "owner", "", List.of());
        var admin = new SiteUserDetails(8L, "admin", "", List.of(new SimpleGrantedAuthority("blog:post:manage")));
        assertEquals(List.of(), controller.list("slug", owner, new MockHttpServletRequest()));
        assertEquals(List.of(), controller.list("slug", admin, new MockHttpServletRequest()));
        draft.delete();
        assertThrows(
                java.util.NoSuchElementException.class,
                () -> controller.list("slug", admin, new MockHttpServletRequest()));
    }
}
