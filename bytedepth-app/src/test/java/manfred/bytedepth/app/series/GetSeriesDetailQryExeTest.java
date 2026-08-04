package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSeriesDetailQryExeTest {

    @Mock
    private SeriesRepository seriesRepository;

    private GetSeriesDetailQryExe qryExe;

    @BeforeEach
    void setUp() {
        qryExe = new GetSeriesDetailQryExe(seriesRepository);
    }

    private Series series() {
        return Series.reconstruct(1L, "专栏A", "series-a", "描述");
    }

    @Test
    void execute_slugNotFound_throwsNoSuchElementException() {
        when(seriesRepository.findBySlug("missing")).thenReturn(Optional.empty());

        var ex = assertThrows(NoSuchElementException.class, () -> qryExe.execute("missing"));
        assertTrue(ex.getMessage().contains("专栏不存在"));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void execute_withPosts_statusNonNull_mappedCorrectly() {
        when(seriesRepository.findBySlug("series-a")).thenReturn(Optional.of(series()));
        when(seriesRepository.findAllPostsBySeries(1L)).thenReturn(List.of(
                new SeriesPostItem(10L, "slug-1", "文章1", 1, null, "PUBLISHED", null),
                new SeriesPostItem(20L, "slug-2", "文章2", 2, null, "DRAFT", null)
        ));

        SeriesDetailDTO result = qryExe.execute("series-a");

        assertEquals(1L, result.getId());
        assertEquals("专栏A", result.getName());
        assertEquals("series-a", result.getSlug());
        assertEquals("描述", result.getDescription());
        assertEquals(2, result.getPosts().size());

        SeriesDetailPostDTO first = result.getPosts().get(0);
        assertEquals(10L, first.getId());
        assertEquals("文章1", first.getTitle());
        assertEquals(1, first.getSeriesOrder());
        assertEquals("PUBLISHED", first.getStatus());

        SeriesDetailPostDTO second = result.getPosts().get(1);
        assertEquals("DRAFT", second.getStatus());
    }

    @Test
    void execute_statusNull_convertedToEmptyString() {
        when(seriesRepository.findBySlug("series-a")).thenReturn(Optional.of(series()));
        when(seriesRepository.findAllPostsBySeries(1L)).thenReturn(List.of(
                new SeriesPostItem(10L, "slug-1", "文章1", 1, null, null, null)
        ));

        SeriesDetailDTO result = qryExe.execute("series-a");

        assertEquals(1, result.getPosts().size());
        assertEquals("", result.getPosts().get(0).getStatus());
    }

    @Test
    void execute_noPosts_returnsEmptyList() {
        when(seriesRepository.findBySlug("series-a")).thenReturn(Optional.of(series()));
        when(seriesRepository.findAllPostsBySeries(1L)).thenReturn(List.of());

        SeriesDetailDTO result = qryExe.execute("series-a");

        assertTrue(result.getPosts().isEmpty());
    }
}
