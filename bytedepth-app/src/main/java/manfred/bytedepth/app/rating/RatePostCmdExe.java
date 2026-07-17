package manfred.bytedepth.app.rating;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.rating.PostRatingRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RatePostCmdExe {

    private final PostRepository postRepository;
    private final PostRatingRepository postRatingRepository;

    public void execute(Long postId, String visitorToken, int score) {
        if (score < 1 || score > 5) {
            throw new DomainException("评分必须是 1 到 5 星");
        }
        var post = postRepository.findById(postId)
                .orElseThrow(() -> new DomainException("文章不存在：" + postId));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new DomainException("只能给已发布文章评分");
        }
        postRatingRepository.upsert(postId, visitorToken, score);
    }
}
