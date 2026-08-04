package manfred.bytedepth.app.post.command;

import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdatePostCmdExeTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private IndexPostCmdExe indexPostCmdExe;

    private UpdatePostCmdExe updatePostCmdExe;

    @BeforeEach
    void setUp() {
        updatePostCmdExe = new UpdatePostCmdExe(postRepository, indexPostCmdExe);
    }

    @Test
    void execute_shouldUpdateContentAndSave() {
        Post existing = Post.reconstruct(1L, "旧标题", "旧内容",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenReturn(existing);

        updatePostCmdExe.execute(1L, "新标题", "新内容");

        assertEquals("新标题", existing.getTitle());
        assertEquals("新内容", existing.getContent());
        verify(postRepository).save(existing);
    }

    @Test
    void execute_shouldThrow_whenPostNotFound() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> updatePostCmdExe.execute(99L, "标题", "内容"));

        assertTrue(ex.getMessage().contains("99"));
        verify(postRepository, never()).save(any());
    }

    @Test
    void execute_threeArgOverload_delegatesToFourArg() {
        Post existing = Post.reconstruct(1L, "旧标题", "旧内容",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenReturn(existing);

        updatePostCmdExe.execute(1L, "新标题", "新内容");

        assertEquals("新标题", existing.getTitle());
        assertEquals("新内容", existing.getContent());
        verify(postRepository).save(existing);
        verify(indexPostCmdExe, never()).execute(any());
    }

    @Test
    void execute_withPublishedPost_triggersIndexing() {
        Post published = Post.reconstruct(1L, "标题", "内容",
                PostStatus.PUBLISHED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(published));
        when(postRepository.save(any(Post.class))).thenReturn(published);

        updatePostCmdExe.execute(1L, "新标题", "新内容", 5L);

        assertEquals("新标题", published.getTitle());
        assertEquals(5L, published.getCategoryId());
        verify(postRepository).save(published);
        verify(indexPostCmdExe).execute(1L);
    }

    @Test
    void execute_withDraftPost_doesNotTriggerIndexing() {
        Post draft = Post.reconstruct(1L, "标题", "内容",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(postRepository.save(any(Post.class))).thenReturn(draft);

        updatePostCmdExe.execute(1L, "新标题", "新内容", 3L);

        verify(indexPostCmdExe, never()).execute(any());
    }

    @Test
    void execute_withCategoryId_assignsCategory() {
        Post existing = Post.reconstruct(1L, "旧", "旧内容",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenReturn(existing);

        updatePostCmdExe.execute(1L, "新", "新内容", 7L);

        assertEquals(7L, existing.getCategoryId());
        verify(postRepository).save(existing);
    }
}
