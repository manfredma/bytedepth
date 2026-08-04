package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSeriesPostsQryExeTest {

    @Mock
    private SeriesRepository seriesRepository;

    private GetSeriesPostsQryExe qryExe;

    @BeforeEach
    void setUp() {
        qryExe = new GetSeriesPostsQryExe(seriesRepository);
    }

    @Test
    void execute_withPosts_mapsToDTO() {
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(List.of(
                new SeriesPostItem(10L, "slug-1", "文章1", 1, "content", "PUBLISHED", null),
                new SeriesPostItem(20L, "slug-2", "文章2", 2, "content", "PUBLISHED", null)
        ));

        List<SeriesPostItemDTO> result = qryExe.execute(1L);

        assertEquals(2, result.size());

        SeriesPostItemDTO first = result.get(0);
        assertEquals(10L, first.getId());
        assertEquals("slug-1", first.getSlug());
        assertEquals("文章1", first.getTitle());
        assertEquals(1, first.getSeriesOrder());

        SeriesPostItemDTO second = result.get(1);
        assertEquals(20L, second.getId());
        assertEquals("slug-2", second.getSlug());
        assertEquals("文章2", second.getTitle());
        assertEquals(2, second.getSeriesOrder());
    }

    @Test
    void execute_noPosts_returnsEmptyList() {
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(List.of());

        List<SeriesPostItemDTO> result = qryExe.execute(1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void execute_nullSlugAndContent_handledByCompactConstructor() {
        // SeriesPostItem compact constructor sets slug/content/status/publishedAt to null
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(List.of(
                new SeriesPostItem(10L, "文章1", 1)
        ));

        List<SeriesPostItemDTO> result = qryExe.execute(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
        assertNull(result.get(0).getSlug());
        assertEquals("文章1", result.get(0).getTitle());
        assertEquals(1, result.get(0).getSeriesOrder());
    }
}
