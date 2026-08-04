package manfred.bytedepth.app.tag;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.tag.TagRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteTagCmdExe {

    private final TagRepository tagRepository;

    public void execute(Long tagId) {
        tagRepository.deleteWithPostAssociations(tagId);
    }
}
