package manfred.bytedepth.app.tag;

import static org.mockito.Mockito.verify;

import manfred.bytedepth.domain.tag.TagRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeleteTagCmdExeTest {

  private final TagRepository tagRepository = Mockito.mock(TagRepository.class);
  private final DeleteTagCmdExe command = new DeleteTagCmdExe(tagRepository);

  @Test
  void execute_removesTagAndItsPostAssociations() {
    command.execute(3L);

    verify(tagRepository).deleteWithPostAssociations(3L);
  }
}
