package manfred.bytedepth.app.post.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;
import manfred.bytedepth.app.post.PostWriteTransaction;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.*;
import org.junit.jupiter.api.Test;

class PostMetadataWriteTest {
    final PostRepository posts = mock(PostRepository.class);
    final IndexPostCmdExe index = mock(IndexPostCmdExe.class);
    final Post stale = post("旧内容", 1), fresh = post("最新内容", 9);
    boolean locked;
    final PostWriteTransaction tx = new PostWriteTransaction() {
        public <T> T withLockedPost(Long id, Function<Post, T> action) {
            locked = true;
            try {
                return action.apply(fresh);
            } finally {
                locked = false;
            }
        }
    };

    static Post post(String content, int version) {
        return Post.reconstruct(
                1L,
                "slug",
                "title",
                content,
                PostStatus.DRAFT,
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                null,
                2L,
                false,
                version);
    }

    void prepare() {
        when(posts.findById(1L)).thenReturn(Optional.of(stale));
        when(posts.save(any())).thenAnswer(inv -> {
            assertTrue(locked, "all full-row saves must share the content writer lock");
            Post saved = inv.getArgument(0);
            assertEquals("最新内容", saved.getContent());
            assertEquals(9, saved.getContentVersion());
            return saved;
        });
        doAnswer(inv -> {
                    assertFalse(locked, "external search must run after commit");
                    return null;
                })
                .when(index)
                .execute(1L);
    }

    @Test
    void U36_publishNeverOverwritesNewContentWithStaleMetadataRead() {
        prepare();
        new PublishPostCmdExe(posts, tx, index).execute(1L);
        assertEquals(PostStatus.PUBLISHED, fresh.getStatus());
    }

    @Test
    void U36_deleteNeverOverwritesNewContentWithStaleMetadataRead() {
        prepare();
        new DeletePostCmdExe(posts, tx, index).execute(1L);
        assertEquals(PostStatus.DELETED, fresh.getStatus());
    }

    @Test
    void U36_featureNeverOverwritesNewContentWithStaleMetadataRead() {
        prepare();
        var command = new FeaturePostCmdExe(posts, tx);
        command.feature(1L);
        assertTrue(fresh.getFeatured());
        command.unfeature(1L);
        assertFalse(fresh.getFeatured());
    }
}
