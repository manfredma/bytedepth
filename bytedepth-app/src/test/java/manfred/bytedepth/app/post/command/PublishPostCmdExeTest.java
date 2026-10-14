package manfred.bytedepth.app.post.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishPostCmdExeTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private IndexPostCmdExe indexPostCmdExe;

    private PublishPostCmdExe publishPostCmdExe;

    @BeforeEach
    void setUp() {
        publishPostCmdExe = new PublishPostCmdExe(postRepository, indexPostCmdExe);
    }

    @Test
    void execute_shouldPublishSaveAndIndex() {
        Post draft = Post.reconstruct(1L, "标题", "内容", PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(postRepository.save(any(Post.class))).thenReturn(draft);

        publishPostCmdExe.execute(1L);

        assertEquals(PostStatus.PUBLISHED, draft.getStatus());
        assertNotNull(draft.getPublishedAt());
        verify(postRepository).save(draft);
        verify(indexPostCmdExe).execute(1L);
    }

    @Test
    void execute_shouldThrow_whenPostNotFound() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> publishPostCmdExe.execute(99L));

        assertTrue(ex.getMessage().contains("99"));
        verify(postRepository, never()).save(any());
        verify(indexPostCmdExe, never()).execute(any());
    }

    @Test
    void execute_retriesIndexingAfterPublishedDatabaseStateSurvivesSearchFailure() {
        Post draft = Post.reconstruct(1L, "标题", "内容", PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(postRepository.save(any())).thenReturn(draft);
        doThrow(new IllegalStateException("search unavailable"))
                .doNothing()
                .when(indexPostCmdExe)
                .execute(1L);

        assertThrows(IllegalStateException.class, () -> publishPostCmdExe.execute(1L));
        LocalDateTime firstPublishedAt = draft.getPublishedAt();
        publishPostCmdExe.execute(1L);

        assertEquals(firstPublishedAt, draft.getPublishedAt());
        verify(postRepository, times(1)).save(draft);
        verify(indexPostCmdExe, times(2)).execute(1L);
    }

    @Test
    void execute_deletedArticleCannotBePublished() {
        Post deleted =
                Post.reconstruct(1L, "标题", "内容", PostStatus.DELETED, LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(deleted));
        assertThrows(RuntimeException.class, () -> publishPostCmdExe.execute(1L));
        verify(postRepository, never()).save(any());
        verifyNoInteractions(indexPostCmdExe);
    }
}
