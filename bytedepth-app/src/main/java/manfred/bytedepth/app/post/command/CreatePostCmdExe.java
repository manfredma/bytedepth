package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.common.SlugUtils;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
        String slug = resolveSlug(cmd.getSlug(), cmd.getTitle());
        Post post = Post.create(cmd.getTitle(), cmd.getContent(), authorId, slug);
        if (cmd.getCategoryId() != null) {
            post.assignCategory(cmd.getCategoryId());
        }
        Post saved = postRepository.save(post);
        return saved.getId();
    }

    private static final DateTimeFormatter SLUG_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 生成唯一 slug：
     * 1. 优先使用用户手动填写的 slug（已校验格式）；
     * 2. 若未填写，从标题提取英文 + 数字片段自动生成；
     * 3. 纯中文标题无法提取英文片段时，fallback 为 {@code post-{yyyyMMddHHmmss}}，
     *    避免与其他文章碰撞产生无意义的 post-2、post-3；
     * 4. 仍有冲突时追加 -2/-3/... 后缀。
     */
    private String resolveSlug(String provided, String title) {
        String base;
        if (provided != null && SlugUtils.isValid(provided)) {
            base = provided.toLowerCase();
        } else {
            base = SlugUtils.slugify(title);
        }
        if (base.isBlank()) {
            // 纯中文/无 ASCII 字符标题：用时间戳生成唯一基础 slug
            base = "post-" + LocalDateTime.now().format(SLUG_TS);
        }
        if (postRepository.findBySlug(base).isEmpty()) return base;
        for (int i = 2; i <= 999; i++) {
            String candidate = base + "-" + i;
            if (postRepository.findBySlug(candidate).isEmpty()) return candidate;
        }
        throw new DomainException("无法生成唯一 slug，请手动指定");
    }
}
