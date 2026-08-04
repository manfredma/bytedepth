package manfred.bytedepth.app.rating;

import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.rating.PostRatingRepository;
import manfred.bytedepth.domain.rating.PostRatingStats;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RatingQueriesTest {

    private final PostRatingRepository ratings = mock(PostRatingRepository.class);

    @Test
    void query_usesAnonymousAndKnownVisitorPaths() {
        when(ratings.getStats(2L)).thenReturn(new PostRatingStats(4.5, 8));
        when(ratings.findScore(2L, "token")).thenReturn(Optional.of(3));
        GetPostRatingQryExe query = new GetPostRatingQryExe(ratings);

        assertNull(query.execute(2L, null).visitorScore());
        assertEquals(3, query.execute(2L, "token").visitorScore());
        assertEquals(4.5, query.execute(2L, "token").averageRating());
    }

    @Test
    void rate_rejectsMissingAndUnpublishedPosts() {
        PostRepository posts = mock(PostRepository.class);
        RatePostCmdExe command = new RatePostCmdExe(posts, ratings);
        when(posts.findById(1L)).thenReturn(Optional.empty());
        assertThrows(DomainException.class, () -> command.execute(1L, "t", 1));

        when(posts.findById(2L)).thenReturn(Optional.of(Post.reconstruct(2L, "p", "t", "c", PostStatus.DRAFT,
                LocalDateTime.now(), null, LocalDateTime.now(), null, null, false)));
        assertThrows(DomainException.class, () -> command.execute(2L, "t", 1));
        verifyNoInteractions(ratings);
    }
}
