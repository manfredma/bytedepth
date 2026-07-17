package manfred.bytedepth.app.rating;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.rating.PostRatingRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetPostRatingQryExe {

    private final PostRatingRepository postRatingRepository;

    public PostRatingDTO execute(Long postId, String visitorToken) {
        var stats = postRatingRepository.getStats(postId);
        Integer visitorScore = visitorToken == null ? null
                : postRatingRepository.findScore(postId, visitorToken).orElse(null);
        return new PostRatingDTO(stats.averageRating(), stats.ratingCount(), visitorScore);
    }
}
