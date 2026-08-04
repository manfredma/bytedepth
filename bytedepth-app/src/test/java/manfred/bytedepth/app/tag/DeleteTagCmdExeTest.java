package manfred.bytedepth.app.tag;

import manfred.bytedepth.domain.tag.TagRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;

class DeleteTagCmdExeTest {

    private final TagRepository tagRepository = Mockito.mock(TagRepository.class);
    private final DeleteTagCmdExe command = new DeleteTagCmdExe(tagRepository);

    @Test
    void execute_removesTagAndItsPostAssociations() {
        command.execute(3L);

        verify(tagRepository).deleteWithPostAssociations(3L);
    }
}
