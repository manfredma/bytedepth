package manfred.bytedepth.app.series;

import static org.mockito.Mockito.verify;

import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemovePostFromSeriesCmdExeTest {

    private final IndexPostCmdExe indexPostCmdExe = org.mockito.Mockito.mock(IndexPostCmdExe.class);

    @Mock
    private PostRepository postRepository;

    private RemovePostFromSeriesCmdExe cmdExe;

    @BeforeEach
    void setUp() {
        cmdExe = new RemovePostFromSeriesCmdExe(postRepository, indexPostCmdExe);
    }

    @Test
    void execute_shouldClearPostSeries() {
        cmdExe.execute(42L);
        verify(postRepository).clearPostSeries(42L);
        verify(indexPostCmdExe).execute(42L);
    }
}
