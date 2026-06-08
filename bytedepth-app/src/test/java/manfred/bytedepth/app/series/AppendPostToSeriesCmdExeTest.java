package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppendPostToSeriesCmdExeTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private SeriesRepository seriesRepository;

    private AppendPostToSeriesCmdExe cmdExe;

    @BeforeEach
    void setUp() {
        cmdExe = new AppendPostToSeriesCmdExe(postRepository, seriesRepository);
    }

    @Test
    void execute_shouldAppendAfterLastPost() {
        when(seriesRepository.findMaxOrderInSeries(10L)).thenReturn(3);

        cmdExe.execute(99L, 10L);

        verify(postRepository).setPostSeries(99L, 10L, 4);
    }

    @Test
    void execute_whenSeriesEmpty_shouldSetOrderTo1() {
        when(seriesRepository.findMaxOrderInSeries(10L)).thenReturn(0);

        cmdExe.execute(99L, 10L);

        verify(postRepository).setPostSeries(99L, 10L, 1);
    }
}
