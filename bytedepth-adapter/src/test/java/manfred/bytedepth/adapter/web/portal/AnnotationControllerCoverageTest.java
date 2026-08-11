package manfred.bytedepth.adapter.web.portal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import manfred.bytedepth.adapter.web.security.SiteUserDetails;
import manfred.bytedepth.app.annotation.CreateAnnotationCmdExe;
import manfred.bytedepth.app.annotation.DeleteAnnotationCmdExe;
import manfred.bytedepth.app.annotation.ListAnnotationsQryExe;
import manfred.bytedepth.app.annotation.UpdateAnnotationCmdExe;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnotationControllerCoverageTest {

    @Test
    void create_loggedInReaderDoesNotCreateAnAnonymousIdentity() {
        PostRepository posts = mock(PostRepository.class);
        CreateAnnotationCmdExe create = mock(CreateAnnotationCmdExe.class);
        AnnotationVisitorIdentity visitorIdentity = mock(AnnotationVisitorIdentity.class);
        AnnotationController controller = new AnnotationController(posts, mock(ListAnnotationsQryExe.class), create,
                mock(DeleteAnnotationCmdExe.class), mock(UpdateAnnotationCmdExe.class), visitorIdentity);
        when(posts.findBySlug("post")).thenReturn(Optional.of(post()));
        when(create.execute(eq(1L), eq(7L), eq(null), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(annotation());

        var result = controller.create("post",
                new AnnotationController.CreateAnnotationRequest("文字", "评论", "yellow", AnnotationVisibility.PUBLIC, 0, 2),
                new SiteUserDetails(7L, "reader", "", List.of(new SimpleGrantedAuthority("blog:post:create"))),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(result.ownedByCurrentVisitor()).isTrue();

        when(posts.findBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.list("missing", null, new MockHttpServletRequest()))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("missing");
    }

    private static Post post() {
        return Post.reconstruct(1L, "post", "标题", "内容", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, 7L, false);
    }

    private static PostAnnotation annotation() {
        return new PostAnnotation(2L, 1L, 7L, null, "文字", "评论", "yellow", AnnotationVisibility.PUBLIC,
                0, 2, LocalDateTime.now());
    }
}
