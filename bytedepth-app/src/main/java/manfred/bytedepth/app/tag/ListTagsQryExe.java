package manfred.bytedepth.app.tag;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.tag.TagRepository;
import manfred.bytedepth.domain.tag.TagWithCount;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListTagsQryExe {

    private final TagRepository tagRepository;

    public List<TagDTO> execute() {
        return tagRepository.findAll().stream()
                .map(t -> { TagDTO dto = new TagDTO(); dto.setId(t.getId()); dto.setName(t.getName()); dto.setSlug(t.getSlug()); return dto; })
                .collect(Collectors.toList());
    }

    public List<TagDTO> findByPostId(Long postId) {
        return tagRepository.findByPostId(postId).stream()
                .map(t -> { TagDTO dto = new TagDTO(); dto.setId(t.getId()); dto.setName(t.getName()); dto.setSlug(t.getSlug()); return dto; })
                .collect(Collectors.toList());
    }

    public List<TagDTO> findAllWithCount() {
        return tagRepository.findAllWithCount().stream()
                .map(t -> { TagDTO dto = new TagDTO(); dto.setId(t.getId()); dto.setName(t.getName()); dto.setSlug(t.getSlug()); dto.setCount(t.getCount()); return dto; })
                .collect(Collectors.toList());
    }
}
