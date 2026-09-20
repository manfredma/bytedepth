package manfred.bytedepth.infrastructure.comment;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommentDeletionContractTest {

    @Test
    void deleteById_removesTheRequestedRow() {
        CommentMapper mapper = mock(CommentMapper.class);
        CommentRepositoryImpl repository = new CommentRepositoryImpl(mapper);

        repository.deleteById(42L);

        verify(mapper).deleteById(42L);
    }
}
