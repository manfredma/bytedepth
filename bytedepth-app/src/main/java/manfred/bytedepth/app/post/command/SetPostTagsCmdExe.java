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

    public void execute(Long postId, List<String> slugs) {
        List<Long> tagIds = slugs.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(raw -> {
                    String slug = raw.toLowerCase().substring(0, Math.min(raw.length(), 100));
                    return tagRepository.findBySlug(slug)
                            .orElseGet(() -> tagRepository.save(Tag.create(raw, slug)));
                })
                .map(Tag::getId)
                .collect(Collectors.toList());
        tagRepository.savePostTags(postId, tagIds);
    }
}
