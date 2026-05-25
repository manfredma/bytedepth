package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreatePostCmdExe {

    private final PostRepository postRepository;

    public Long execute(CreatePostCmd cmd) {
        Post post = Post.create(cmd.getTitle(), cmd.getContent());
        if (cmd.getCategoryId() != null) {
            post.assignCategory(cmd.getCategoryId());
        }
        Post saved = postRepository.save(post);
        return saved.getId();
    }
}
