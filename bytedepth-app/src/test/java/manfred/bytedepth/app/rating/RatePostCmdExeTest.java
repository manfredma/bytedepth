package manfred.bytedepth.app.rating;

import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.rating.PostRatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatePostCmdExeTest {

    @Mock private PostRepository postRepository;
    @Mock private PostRatingRepository postRatingRepository;
    private RatePostCmdExe exe;

    @BeforeEach
    void setUp() {
        exe = new RatePostCmdExe(postRepository, postRatingRepository);
    }

    @Test
    void execute_publishedPost_upsertsVisitorRating() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post(1L, PostStatus.PUBLISHED)));

        exe.execute(1L, "visitor-token", 5);

        verify(postRatingRepository).upsert(1L, "visitor-token", 5);
    }

    @Test
    void execute_scoreOutsideRange_rejectsBeforeWriting() {
        assertThrows(DomainException.class, () -> exe.execute(1L, "visitor-token", 0));

        verify(postRatingRepository, never()).upsert(1L, "visitor-token", 0);
    }

    @Test
    void execute_scoreAboveFive_rejectsBeforeWriting() {
        assertThrows(DomainException.class, () -> exe.execute(1L, "visitor-token", 6));

        verify(postRatingRepository, never()).upsert(1L, "visitor-token", 6);
    }

    private Post post(Long id, PostStatus status) {
        return Post.reconstruct(id, "article", "title", "content", status,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false);
    }
}
