package manfred.bytedepth.app.post.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeaturePostCmdExeTest {

  @Mock private PostRepository postRepository;
  private FeaturePostCmdExe exe;

  @BeforeEach
  void setUp() {
    exe = new FeaturePostCmdExe(postRepository);
  }

  @Test
  void feature_setsFeatureTrue() {
    Post post =
        Post.reconstruct(
            1L,
            "T",
            "C",
            PostStatus.PUBLISHED,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            1L,
            false);
    when(postRepository.findById(1L)).thenReturn(Optional.of(post));

    exe.feature(1L);

    verify(postRepository).save(argThat(p -> Boolean.TRUE.equals(p.getFeatured())));
  }

  @Test
  void feature_withReason_persistsEditorialReason() {
    Post post =
        Post.reconstruct(
            1L,
            "T",
            "C",
            PostStatus.PUBLISHED,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            1L,
            false);
    when(postRepository.findById(1L)).thenReturn(Optional.of(post));

    exe.feature(1L, "适合作为首页入口");

    verify(postRepository).save(argThat(p -> "适合作为首页入口".equals(p.getFeaturedReason())));
  }

  @Test
  void unfeature_setsFeaturedFalse() {
    Post post =
        Post.reconstruct(
            1L,
            "T",
            "C",
            PostStatus.PUBLISHED,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            1L,
            true);
    when(postRepository.findById(1L)).thenReturn(Optional.of(post));

    exe.unfeature(1L);

    verify(postRepository).save(argThat(p -> !Boolean.TRUE.equals(p.getFeatured())));
  }

  @Test
  void feature_throws_whenPostNotFound() {
    when(postRepository.findById(404L)).thenReturn(Optional.empty());

    DomainException ex = assertThrows(DomainException.class, () -> exe.feature(404L));

    assertTrue(ex.getMessage().contains("404"));
    verify(postRepository, never()).save(any());
  }

  @Test
  void unfeature_throws_whenPostNotFound() {
    when(postRepository.findById(404L)).thenReturn(Optional.empty());

    DomainException ex = assertThrows(DomainException.class, () -> exe.unfeature(404L));

    assertTrue(ex.getMessage().contains("404"));
    verify(postRepository, never()).save(any());
  }
}
