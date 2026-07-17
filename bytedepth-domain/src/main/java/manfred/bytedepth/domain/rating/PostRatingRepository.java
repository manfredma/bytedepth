package manfred.bytedepth.domain.rating;

import java.util.Optional;

public interface PostRatingRepository {
    void upsert(Long postId, String visitorToken, int score);
    PostRatingStats getStats(Long postId);
    Optional<Integer> findScore(Long postId, String visitorToken);
}
