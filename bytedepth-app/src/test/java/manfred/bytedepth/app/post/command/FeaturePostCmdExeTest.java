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
}
