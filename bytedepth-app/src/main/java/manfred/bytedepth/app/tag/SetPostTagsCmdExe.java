package manfred.bytedepth.app.tag;

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
     * 设置文章标签。tagSpecs 格式为 "slug:显示名"，无冒号则 name = slug。
     * 不存在的标签自动创建，已有标签直接复用，最终替换原文章所有标签。
     */
    public void execute(Long postId, List<String> tagSpecs) {
        List<Long> tagIds = tagSpecs.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(spec -> {
                    String[] parts = spec.split(":", 2);
                    String slug = parts[0].trim();
                    String name = parts.length > 1 ? parts[1].trim() : slug;
                    return tagRepository.findBySlug(slug)
                            .orElseGet(() -> tagRepository.save(Tag.create(name, slug)));
                })
                .map(Tag::getId)
                .collect(Collectors.toList());
        tagRepository.savePostTags(postId, tagIds);
    }
}
