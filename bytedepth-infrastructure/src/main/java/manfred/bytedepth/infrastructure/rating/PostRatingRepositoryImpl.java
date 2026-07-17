package manfred.bytedepth.infrastructure.rating;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.rating.PostRatingRepository;
import manfred.bytedepth.domain.rating.PostRatingStats;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PostRatingRepositoryImpl implements PostRatingRepository {

    private final PostRatingMapper postRatingMapper;

    @Override
    public void upsert(Long postId, String visitorToken, int score) {
        postRatingMapper.upsert(postId, visitorToken, score);
    }

    @Override
    public PostRatingStats getStats(Long postId) {
        PostRatingStatsDO stats = postRatingMapper.findStats(postId);
        return new PostRatingStats(
                stats.getAverageRating() == null ? 0D : stats.getAverageRating(),
                stats.getRatingCount() == null ? 0L : stats.getRatingCount());
    }

    @Override
    public Optional<Integer> findScore(Long postId, String visitorToken) {
        return postRatingMapper.findScore(postId, visitorToken);
    }
}
