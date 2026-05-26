package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdatePostCmdExe {

    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;

    public void execute(Long id, String title, String content) {
        execute(id, title, content, null);
    }

    public void execute(Long id, String title, String content, Long categoryId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("博文不存在：" + id));
        post.updateContent(title, content);
        post.assignCategory(categoryId);
        postRepository.save(post);
        if (post.getStatus() == PostStatus.PUBLISHED) {
            indexPostCmdExe.execute(id);
        }
    }
}
