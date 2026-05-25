package manfred.bytedepth.app.post.command;

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
class DeletePostCmdExeTest {

    @Mock
    private PostRepository postRepository;

    private DeletePostCmdExe deletePostCmdExe;

    @BeforeEach
    void setUp() {
        deletePostCmdExe = new DeletePostCmdExe(postRepository);
    }

    @Test
    void execute_shouldDeletePostAndSave() {
        Post existing = Post.reconstruct(1L, "标题", "内容",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenReturn(existing);

        deletePostCmdExe.execute(1L);

        assertEquals(PostStatus.DELETED, existing.getStatus());
        verify(postRepository).save(existing);
    }

    @Test
    void execute_shouldThrow_whenPostNotFound() {
        when(postRepository.findById(42L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> deletePostCmdExe.execute(42L));

        assertTrue(ex.getMessage().contains("42"));
        verify(postRepository, never()).save(any());
    }
}
