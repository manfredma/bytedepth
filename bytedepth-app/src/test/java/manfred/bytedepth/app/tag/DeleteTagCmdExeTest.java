package manfred.bytedepth.app.tag;

import static org.mockito.Mockito.verify;

import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.tag.TagRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DeleteTagCmdExeTest {

    private final PostRepository postRepository = org.mockito.Mockito.mock(PostRepository.class);

    private final IndexPostCmdExe indexPostCmdExe = org.mockito.Mockito.mock(IndexPostCmdExe.class);

    private final TagRepository tagRepository = Mockito.mock(TagRepository.class);
    private final DeleteTagCmdExe command = new DeleteTagCmdExe(tagRepository, postRepository, indexPostCmdExe);

    @Test
    void execute_removesTagAndItsPostAssociations() {
        command.execute(3L);

        verify(tagRepository).deleteWithPostAssociations(3L);
    }
}
