package manfred.bytedepth.app.tag;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.tag.Tag;
import manfred.bytedepth.domain.tag.TagRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateTagCmdExe {

    private final TagRepository tagRepository;

    public TagDTO execute(String name, String slug) {
        Tag tag = tagRepository.findBySlug(slug)
                .orElseGet(() -> tagRepository.save(Tag.create(name, slug)));
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setSlug(tag.getSlug());
        return dto;
    }
}
