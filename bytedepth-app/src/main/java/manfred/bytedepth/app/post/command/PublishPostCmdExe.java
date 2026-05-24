package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublishPostCmdExe {

    private final PostRepository postRepository;

    public void execute(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("博文不存在：" + postId));
        post.publish();
        postRepository.save(post);
    }
}
