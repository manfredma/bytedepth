package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdatePostSlugCmdExe {
    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;

    /** 调用方负责 slug 格式、唯一性和权限校验。 */
    public void execute(Long postId, String slug) {
        postRepository.updateSlug(postId, slug);
        indexPostCmdExe.execute(postId);
    }
}
