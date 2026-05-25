package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeletePostCmdExe {

    private final PostRepository postRepository;

    public void execute(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("博文不存在：" + id));
        post.delete();
        postRepository.save(post);
    }
}
