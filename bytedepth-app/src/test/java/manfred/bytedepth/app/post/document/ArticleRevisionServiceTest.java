package manfred.bytedepth.app.post.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;
import manfred.bytedepth.app.annotation.AnnotationRepositoryPort;
import manfred.bytedepth.app.annotation.LegacyAnchorMigrator;
import manfred.bytedepth.app.post.PostContentRevisionPort;
import manfred.bytedepth.app.post.PostWriteTransaction;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostContentRevision;
import manfred.bytedepth.domain.post.PostStatus;
import org.junit.jupiter.api.Test;

class ArticleRevisionServiceTest {
    private final PostContentRevisionPort revisions = mock(PostContentRevisionPort.class);
    private final AnnotationRepositoryPort annotations = mock(AnnotationRepositoryPort.class);
    private final ArticleDocumentService documents = new ArticleDocumentService();
    private final Post post =
            Post.reconstruct(1L, "title", "原文", PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now());
    private final PostWriteTransaction transaction = new PostWriteTransaction() {
        public <T> T withLockedPost(Long id, Function<Post, T> action) {
            return action.apply(post);
        }
    };
    private final ArticleRevisionService service =
            new ArticleRevisionService(transaction, revisions, documents, annotations, new LegacyAnchorMigrator());

    @Test
    void U36_titleOnlyRevisionAndBaselineRace() {
        post.updateContent("新标题", "原文");
        var result = service.current(1);
        assertEquals(2, result.revision().contentVersion());
        assertEquals("原文", result.rendered().document().text());
        assertEquals(result.revision().document(), result.rendered().document());
        assertTrue(result.rendered().html().contains("原文"));
        verify(revisions)
                .insert(new PostContentRevision(1, 2, "原文", result.rendered().document(), true));
        when(revisions.find(1, 2)).thenReturn(Optional.of(result.revision()));
        assertEquals(result.revision(), service.current(1).revision());
        verify(revisions, times(1)).insert(any());
    }

    @Test
    void U12_existingRevisionCannotSilentlyChange() {
        when(revisions.find(1, 1))
                .thenReturn(Optional.of(new PostContentRevision(
                        1, 1, "其他", documents.render("其他").document(), true)));
        assertThrows(IllegalStateException.class, () -> service.current(1));
        when(revisions.find(1, 1))
                .thenReturn(Optional.of(new PostContentRevision(
                        1, 1, "原文", documents.render("不同模型").document(), true)));
        assertThrows(IllegalStateException.class, () -> service.current(1));
        verify(revisions, never()).insert(any());
    }

    @Test
    void U41_initialReadClassifiesOnceWithoutRewritingOriginalFields() {
        var now = LocalDateTime.now();
        var legacy = new manfred.bytedepth.domain.annotation.PostAnnotation(
                9L,
                1L,
                2L,
                null,
                "原文",
                "评论",
                "yellow",
                manfred.bytedepth.domain.annotation.AnnotationVisibility.PUBLIC,
                0,
                2,
                now,
                false);
        when(annotations.findByPostId(1L)).thenReturn(java.util.List.of(legacy));
        service.current(1);
        var anchor = org.mockito.ArgumentCaptor.forClass(manfred.bytedepth.domain.annotation.AnnotationAnchor.class);
        verify(annotations).initializeAnchor(eq(9L), anchor.capture(), eq(1L));
        assertEquals("原文", anchor.getValue().exact());
        assertEquals(1, anchor.getValue().sourceRevision());
        var classified = new manfred.bytedepth.domain.annotation.PostAnnotation(
                9L,
                1L,
                2L,
                null,
                "原文",
                "评论",
                "yellow",
                manfred.bytedepth.domain.annotation.AnnotationVisibility.PUBLIC,
                0,
                2,
                now,
                false,
                anchor.getValue(),
                1L);
        var quarantine = new manfred.bytedepth.domain.annotation.PostAnnotation(
                10L,
                1L,
                2L,
                null,
                "缺失",
                "评论",
                "yellow",
                manfred.bytedepth.domain.annotation.AnnotationVisibility.PUBLIC,
                0,
                2,
                now,
                true,
                null,
                1L);
        when(annotations.findByPostId(1L)).thenReturn(java.util.List.of(classified, quarantine));
        service.current(1);
        verify(annotations, times(1)).initializeAnchor(anyLong(), any(), anyLong());
        verify(annotations, never()).update(any());
        verify(annotations, never()).delete(any());
    }
}
