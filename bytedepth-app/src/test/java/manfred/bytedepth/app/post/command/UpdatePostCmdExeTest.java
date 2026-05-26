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
}
