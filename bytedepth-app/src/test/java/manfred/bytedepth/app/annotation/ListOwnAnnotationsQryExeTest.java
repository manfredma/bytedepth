package manfred.bytedepth.app.annotation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import manfred.bytedepth.app.post.document.ArticleRevisionService;
import manfred.bytedepth.app.post.query.PublicPostAccess;
import org.junit.jupiter.api.Test;

class ListOwnAnnotationsQryExeTest {
    final AnnotationRepositoryPort repo = mock(AnnotationRepositoryPort.class);
    final PublicPostAccess access = mock(PublicPostAccess.class);
    final ArticleRevisionService current = mock(ArticleRevisionService.class);
    final ListAnnotationsQryExe views = mock(ListAnnotationsQryExe.class);
    final ListOwnAnnotationsQryExe query = new ListOwnAnnotationsQryExe(repo, access, current, views);

    @Test
    void usesDatabaseOwnPublishedPageAndCount() {
        when(repo.findOwnPublished(5L, 2, 10)).thenReturn(List.of());
        when(repo.countOwnPublished(5L)).thenReturn(13L);
        var result = query.execute(5L, 2, 10);
        assertThat(result.total()).isEqualTo(13);
        assertThat(result.items()).isEmpty();
        verify(repo).findOwnPublished(5L, 2, 10);
        verifyNoInteractions(access);
    }

    @Test
    void rejectsAnonymousAndOverflowBeforeReading() {
        assertThatThrownBy(() -> query.execute(null, 1, 10)).isInstanceOf(AnnotationOwnershipException.class);
        for (int[] p : new int[][] {{0, 10}, {1, 0}, {1, 51}, {Integer.MAX_VALUE, 50}})
            assertThatThrownBy(() -> query.execute(5L, p[0], p[1])).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void locksEveryReturnedPublishedPostBeforeComputingCurrentPlacement() {
        var a = new manfred.bytedepth.domain.annotation.PostAnnotation(
                9L,
                1L,
                5L,
                null,
                "quote",
                null,
                "yellow",
                manfred.bytedepth.domain.annotation.AnnotationVisibility.PRIVATE,
                0,
                5,
                java.time.LocalDateTime.now(),
                false);
        when(repo.findOwnPublished(5L, 1, 10)).thenReturn(List.of(a));
        when(repo.findById(9L)).thenReturn(Optional.of(a));
        when(repo.countOwnPublished(5L)).thenReturn(1L);
        var post = mock(manfred.bytedepth.domain.post.Post.class);
        var rendered = new manfred.bytedepth.app.post.document.ArticleDocumentService().render("quote");
        var target = new manfred.bytedepth.domain.post.PostContentRevision(1, 7, "quote", rendered.document(), false);
        when(current.ensureCurrent(post))
                .thenReturn(new ArticleRevisionService.RevisionedArticle(post, target, rendered));
        var view = mock(AnnotationView.class);
        when(views.view(a, target)).thenReturn(view);
        when(access.withPublishedPost(eq(1L), any())).thenAnswer(i -> {
            java.util.function.Function<manfred.bytedepth.domain.post.Post, Object> action = i.getArgument(1);
            return action.apply(post);
        });
        var result = query.execute(5L, 1, 10);
        assertThat(result.items()).containsExactly(view);
        assertThat(result.total()).isEqualTo(1);
        verify(current).ensureCurrent(post);
    }

    @Test
    void firstMigrationMustUseRefreshedAnchorInsteadOfPrePageSnapshot() {
        var a = new manfred.bytedepth.domain.annotation.PostAnnotation(
                9L,
                1L,
                5L,
                null,
                "quote",
                null,
                "yellow",
                manfred.bytedepth.domain.annotation.AnnotationVisibility.PRIVATE,
                0,
                5,
                java.time.LocalDateTime.now(),
                false);
        when(repo.findOwnPublished(5L, 1, 10)).thenReturn(List.of(a));
        var post = mock(manfred.bytedepth.domain.post.Post.class);
        var rendered = new manfred.bytedepth.app.post.document.ArticleDocumentService().render("quote");
        var target = new manfred.bytedepth.domain.post.PostContentRevision(1, 7, "quote", rendered.document(), false);
        var anchor = new manfred.bytedepth.domain.annotation.AnnotationAnchor(
                1,
                7,
                1,
                rendered.document().digest(),
                "quote",
                "",
                "",
                0,
                5,
                List.of(new manfred.bytedepth.domain.post.document.SegmentSpan("s0", 0, 5)),
                java.util.UUID.randomUUID());
        var fresh = new manfred.bytedepth.domain.annotation.PostAnnotation(
                a.id(),
                a.postId(),
                a.userId(),
                null,
                a.selectedText(),
                null,
                a.color(),
                a.visibility(),
                0,
                5,
                a.createdAt(),
                false,
                anchor,
                7L);
        when(repo.findById(9L)).thenReturn(Optional.of(fresh));
        when(current.ensureCurrent(post))
                .thenReturn(new ArticleRevisionService.RevisionedArticle(post, target, rendered));
        var view = mock(AnnotationView.class);
        when(views.view(fresh, target)).thenReturn(view);
        when(access.withPublishedPost(eq(1L), any())).thenAnswer(i -> {
            java.util.function.Function<manfred.bytedepth.domain.post.Post, Object> action = i.getArgument(1);
            return action.apply(post);
        });
        assertThat(query.execute(5L, 1, 10).items()).containsExactly(view);
        var order = inOrder(current, repo, views);
        order.verify(repo).findOwnPublished(5L, 1, 10);
        order.verify(current).ensureCurrent(post);
        order.verify(repo).findById(9L);
        order.verify(views).view(fresh, target);
    }

    @Test
    void removedAnnotationWhileWaitingForPostLockCannotReappear() {
        var a = new manfred.bytedepth.domain.annotation.PostAnnotation(
                9L,
                1L,
                5L,
                null,
                "quote",
                null,
                "yellow",
                manfred.bytedepth.domain.annotation.AnnotationVisibility.PRIVATE,
                0,
                5,
                java.time.LocalDateTime.now(),
                false);
        when(repo.findOwnPublished(5L, 1, 10)).thenReturn(List.of(a));
        var post = mock(manfred.bytedepth.domain.post.Post.class);
        var rendered = new manfred.bytedepth.app.post.document.ArticleDocumentService().render("quote");
        var target = new manfred.bytedepth.domain.post.PostContentRevision(1, 7, "quote", rendered.document(), false);
        when(current.ensureCurrent(post))
                .thenReturn(new ArticleRevisionService.RevisionedArticle(post, target, rendered));
        when(access.withPublishedPost(eq(1L), any())).thenAnswer(i -> {
            java.util.function.Function<manfred.bytedepth.domain.post.Post, Object> action = i.getArgument(1);
            return action.apply(post);
        });
        assertThatThrownBy(() -> query.execute(5L, 1, 10)).isInstanceOf(AnnotationOwnershipException.class);
        verifyNoInteractions(views);
    }
}
