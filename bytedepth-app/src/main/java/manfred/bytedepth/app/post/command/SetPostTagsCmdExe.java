package manfred.bytedepth.app.post.command;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.tag.Tag;
import manfred.bytedepth.domain.tag.TagRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SetPostTagsCmdExe {

    private final TagRepository tagRepository;

    /**
     * tagSpecs 支持两种格式：
     *   "slug"        → name = slug
     *   "slug:显示名"  → name = 显示名
     */
    public void execute(Long postId, List<String> tagSpecs) {
        List<Long> tagIds = tagSpecs.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(spec -> {
                    String[] parts = spec.split(":", 2);
                    String slug = parts[0].trim().toLowerCase();
                    String name = parts.length > 1 ? parts[1].trim() : slug;
                    return tagRepository.findBySlug(slug)
                            .orElseGet(() -> tagRepository.save(Tag.create(name, slug)));
                })
                .map(Tag::getId)
                .collect(Collectors.toList());
        tagRepository.savePostTags(postId, tagIds);
    }
}
