package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreatePostCmdExe {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public Long execute(CreatePostCmd cmd) {
        Long authorId = null;
        if (cmd.getAuthorUsername() != null) {
            authorId = userRepository.findByUsername(cmd.getAuthorUsername())
                .orElseThrow(() -> new DomainException("用户不存在：" + cmd.getAuthorUsername()))
                .getId();
        }
        Post post = Post.create(cmd.getTitle(), cmd.getContent(), authorId);
        if (cmd.getCategoryId() != null) {
            post.assignCategory(cmd.getCategoryId());
        }
        Post saved = postRepository.save(post);
        return saved.getId();
    }
}
