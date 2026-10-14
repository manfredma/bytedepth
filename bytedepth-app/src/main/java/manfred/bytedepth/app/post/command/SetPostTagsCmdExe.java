package manfred.bytedepth.app.post.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.tag.Tag;
import manfred.bytedepth.domain.tag.TagRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SetPostTagsCmdExe {

    private final TagRepository tagRepository;
    private final PostRepository postRepository;
    private final IndexPostCmdExe indexPostCmdExe;

    /** tagSpecs 支持两种格式： "slug" → name = slug "slug:显示名" → name = 显示名 */
    public void execute(Long postId, List<String> tagSpecs) {
        Set<Long> affectedPostIds = new LinkedHashSet<>();
        affectedPostIds.add(postId);
        List<Long> tagIds = tagSpecs.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(spec -> {
                    String[] parts = spec.split(":", 2);
                    String slug = parts[0].trim().toLowerCase();
                    String name = parts.length > 1 ? parts[1].trim() : slug;
                    return tagRepository
                            .findBySlug(slug)
                            .map(existing -> {
                                // 已有标签但名称不同时更新显示名
                                if (!name.equals(existing.getName())) {
                                    affectedPostIds.addAll(findPublishedPostIds(slug));
                                    return tagRepository.save(Tag.reconstruct(existing.getId(), name, slug));
                                }
                                return existing;
                            })
                            .orElseGet(() -> tagRepository.save(Tag.create(name, slug)));
                })
                .map(Tag::getId)
                .collect(Collectors.toList());
        tagRepository.savePostTags(postId, tagIds);
        affectedPostIds.forEach(indexPostCmdExe::execute);
    }

    private List<Long> findPublishedPostIds(String tagSlug) {
        List<Long> ids = new ArrayList<>();
        int page = 1;
        List<Post> posts;
        while (!(posts = postRepository.findPublishedByTag(tagSlug, page++, 100)).isEmpty()) {
            posts.stream().map(Post::getId).forEach(ids::add);
        }
        return ids;
    }
}
