package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdatePostSlugCmdExe {
    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;

    /** 调用方负责唯一性和权限校验；不可访问的 slug 在应用端口再次拒绝。 */
    public void execute(Long postId, String slug) {
        if (!Post.isValidSlug(slug)) throw new DomainException("文章 slug 格式无效或为保留的纯数字标识");
        postRepository.updateSlug(postId, slug);
        indexPostCmdExe.execute(postId);
    }
}
