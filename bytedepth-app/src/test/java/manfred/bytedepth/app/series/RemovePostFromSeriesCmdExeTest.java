package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.post.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RemovePostFromSeriesCmdExeTest {

    @Mock
    private PostRepository postRepository;

    private RemovePostFromSeriesCmdExe cmdExe;

    @BeforeEach
    void setUp() {
        cmdExe = new RemovePostFromSeriesCmdExe(postRepository);
    }

    @Test
    void execute_shouldClearPostSeries() {
        cmdExe.execute(42L);
        verify(postRepository).clearPostSeries(42L);
    }
}
