package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeaturePostCmdExe {

    private final PostRepository postRepository;
    private final manfred.bytedepth.app.post.PostWriteTransaction transactions;

    public void feature(Long postId) {
        feature(postId, null);
    }

    public void feature(Long postId, String reason) {
        transactions.withLockedPost(postId, post -> {
            post.feature(reason);
            postRepository.save(post);
            return null;
        });
    }

    public void unfeature(Long postId) {
        transactions.withLockedPost(postId, post -> {
            post.unfeature();
            postRepository.save(post);
            return null;
        });
    }
}
