package manfred.bytedepth.app.post.command;

import manfred.bytedepth.domain.common.DomainException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeaturePostCmdExeTest {

    @Mock private PostRepository postRepository;
    private FeaturePostCmdExe exe;

    @BeforeEach
    void setUp() { exe = new FeaturePostCmdExe(postRepository); }

    @Test
    void feature_setsFeatureTrue() {
        Post post = Post.reconstruct(1L, "T", "C", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                null, 1L, false);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        exe.feature(1L);

        verify(postRepository).save(argThat(p -> Boolean.TRUE.equals(p.getFeatured())));
    }

    @Test
    void unfeature_setsFeaturedFalse() {
        Post post = Post.reconstruct(1L, "T", "C", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                null, 1L, true);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        exe.unfeature(1L);

        verify(postRepository).save(argThat(p -> !Boolean.TRUE.equals(p.getFeatured())));
    }

    @Test
    void feature_throws_whenPostNotFound() {
        when(postRepository.findById(404L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> exe.feature(404L));

        assertTrue(ex.getMessage().contains("404"));
        verify(postRepository, never()).save(any());
    }

    @Test
    void unfeature_throws_whenPostNotFound() {
        when(postRepository.findById(404L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> exe.unfeature(404L));

        assertTrue(ex.getMessage().contains("404"));
        verify(postRepository, never()).save(any());
    }
}
