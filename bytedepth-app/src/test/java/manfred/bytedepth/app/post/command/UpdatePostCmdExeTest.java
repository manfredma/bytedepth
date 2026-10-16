package manfred.bytedepth.app.post.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import manfred.bytedepth.app.annotation.*;
import manfred.bytedepth.app.post.*;
import manfred.bytedepth.app.post.document.*;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.*;
import org.junit.jupiter.api.Test;

class UpdatePostCmdExeTest {
    final PostRepository posts = mock(PostRepository.class);
    final AnnotationRepositoryPort annotations = mock(AnnotationRepositoryPort.class);
    final PostContentRevisionPort revisions = mock(PostContentRevisionPort.class);
    final IndexPostCmdExe index = mock(IndexPostCmdExe.class);
    final Post post =
            Post.reconstruct(1L, "title", "原文", PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now());
    final ArrayList<PostContentRevision> saved = new ArrayList<>();
    boolean locked;
    final PostWriteTransaction tx = new PostWriteTransaction() {
        public <T> T withLockedPost(Long id, Function<Post, T> action) {
            locked = true;
            try {
                return action.apply(post);
            } finally {
                locked = false;
            }
        }
    };
    final ArticleRevisionService current = new ArticleRevisionService(
            tx, revisions, new ArticleDocumentService(), annotations, new LegacyAnchorMigrator());
    final UpdatePostCmdExe update = new UpdatePostCmdExe(posts, index, tx, current);

    @Test
    void U02_U04_U37_bodyAndSnapshotsSavedInsideLockIndexOutsideAndNoAnnotationRewrite() {
        doAnswer(inv -> {
                    assertTrue(locked);
                    saved.add(inv.getArgument(0));
                    return null;
                })
                .when(revisions)
                .insert(any());
        when(posts.save(any())).thenAnswer(inv -> {
            assertTrue(locked);
            return inv.getArgument(0);
        });
        doAnswer(inv -> {
                    assertFalse(locked);
                    return null;
                })
                .when(index)
                .execute(1L);
        update.execute(1L, "new", "替换正文", 5L);
        assertEquals(2, post.getContentVersion());
        assertEquals(5, post.getCategoryId());
        assertEquals(
                java.util.List.of("原文", "替换正文"),
                saved.stream().map(PostContentRevision::markdown).toList());
        assertTrue(saved.getFirst().baseline());
        assertFalse(saved.getLast().baseline());
        verify(annotations, never()).update(any());
        verify(annotations, never()).delete(any());
        verify(index).execute(1L);
    }

    @Test
    void U36_titleOnlyRevisionAndUnchangedSave() {
        var baseline = current.current(1).revision();
        when(revisions.find(1, 1)).thenReturn(Optional.of(baseline));
        update.execute(1L, "renamed", "原文");
        assertEquals(2, post.getContentVersion());
        var captured = org.mockito.ArgumentCaptor.forClass(PostContentRevision.class);
        verify(revisions, times(2)).insert(captured.capture());
        assertEquals(baseline.document(), captured.getValue().document());
        when(revisions.find(1, 2)).thenReturn(Optional.of(captured.getValue()));
        update.execute(1L, "renamed", "原文");
        assertEquals(2, post.getContentVersion());
        verify(revisions, times(2)).insert(any());
    }

    @Test
    void U37_snapshotFailureNeverIndexes() {
        doThrow(new IllegalStateException("storage failed")).when(revisions).insert(any());
        assertThrows(IllegalStateException.class, () -> update.execute(1L, "new", "body"));
        verifyNoInteractions(posts, index);
    }
}
