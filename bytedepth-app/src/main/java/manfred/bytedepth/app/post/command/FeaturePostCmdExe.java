package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeaturePostCmdExe {

    private final PostRepository postRepository;

    public void feature(Long postId) {
        var post = postRepository.findById(postId)
            .orElseThrow(() -> new DomainException("文章不存在：" + postId));
        post.feature();
        postRepository.save(post);
    }

    public void unfeature(Long postId) {
        var post = postRepository.findById(postId)
            .orElseThrow(() -> new DomainException("文章不存在：" + postId));
        post.unfeature();
        postRepository.save(post);
    }
}
