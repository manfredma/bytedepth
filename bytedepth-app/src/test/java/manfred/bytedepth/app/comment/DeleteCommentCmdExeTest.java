package manfred.bytedepth.app.comment;

import manfred.bytedepth.domain.comment.CommentRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeleteCommentCmdExeTest {

    @Test
    void execute_physicallyDeletesTheRequestedComment() {
        CommentRepository repository = mock(CommentRepository.class);

        new DeleteCommentCmdExe(repository).execute(42L);

        verify(repository).deleteById(42L);
    }
}
