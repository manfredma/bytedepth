package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovePostInSeriesCmdExeTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private SeriesRepository seriesRepository;

    private MovePostInSeriesCmdExe cmdExe;

    @BeforeEach
    void setUp() {
        cmdExe = new MovePostInSeriesCmdExe(postRepository, seriesRepository);
    }

    // 专栏中有 3 篇文章：order 1=postId 10, order 2=postId 20, order 3=postId 30
    private List<SeriesPostItem> threePostSeries() {
        return List.of(
                new SeriesPostItem(10L, "文章A", 1),
                new SeriesPostItem(20L, "文章B", 2),
                new SeriesPostItem(30L, "文章C", 3)
        );
    }

    @Test
    void moveUp_middlePost_swapsWithPrev() {
        when(seriesRepository.findAllPostsBySeries(100L)).thenReturn(threePostSeries());

        cmdExe.execute(100L, 20L, MovePostInSeriesCmdExe.Direction.UP);

        // postId=20 order 2→1, postId=10 order 1→2
        verify(postRepository).setPostSeries(20L, 100L, 1);
        verify(postRepository).setPostSeries(10L, 100L, 2);
    }

    @Test
    void moveDown_middlePost_swapsWithNext() {
        when(seriesRepository.findAllPostsBySeries(100L)).thenReturn(threePostSeries());

        cmdExe.execute(100L, 20L, MovePostInSeriesCmdExe.Direction.DOWN);

        // postId=20 order 2→3, postId=30 order 3→2
        verify(postRepository).setPostSeries(20L, 100L, 3);
        verify(postRepository).setPostSeries(30L, 100L, 2);
    }

    @Test
    void moveUp_firstPost_doesNothing() {
        when(seriesRepository.findAllPostsBySeries(100L)).thenReturn(threePostSeries());

        cmdExe.execute(100L, 10L, MovePostInSeriesCmdExe.Direction.UP);

        verify(postRepository, never()).setPostSeries(anyLong(), anyLong(), anyInt());
    }

    @Test
    void moveDown_lastPost_doesNothing() {
        when(seriesRepository.findAllPostsBySeries(100L)).thenReturn(threePostSeries());

        cmdExe.execute(100L, 30L, MovePostInSeriesCmdExe.Direction.DOWN);

        verify(postRepository, never()).setPostSeries(anyLong(), anyLong(), anyInt());
    }
}
