package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSeriesQryExeTest {

    @Mock
    private SeriesRepository seriesRepository;

    private ListSeriesQryExe qryExe;

    @BeforeEach
    void setUp() {
        qryExe = new ListSeriesQryExe(seriesRepository);
    }

    private Series series(long id, String name) {
        return Series.reconstruct(id, name, "slug-" + id, "描述" + id);
    }

    private SeriesPostItem post(long id, int order, String content) {
        return new SeriesPostItem(id, "slug-" + id, "文章" + id, order, content, "PUBLISHED", null);
    }

    @Test
    void execute_withPosts_mapsCardCorrectly() {
        when(seriesRepository.findAll()).thenReturn(List.of(
                series(1L, "专栏A"),
                series(2L, "专栏B")
        ));
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(List.of(
                post(10L, 1, "这是第一篇文章的正文，内容足够长。"),
                post(20L, 2, "第二篇文章内容。")
        ));
        when(seriesRepository.findPublishedPostsBySeries(2L)).thenReturn(List.of(
                post(30L, 1, "专栏B唯一文章。")
        ));

        ListSeriesQryExe.PageResult result = qryExe.execute(1);

        assertEquals(2, result.total());
        assertEquals(1, result.currentPage());
        assertEquals(1, result.totalPages());
        assertEquals(2, result.series().size());

        SeriesCardDTO card1 = result.series().get(0);
        assertEquals(1L, card1.getId());
        assertEquals("专栏A", card1.getName());
        assertEquals("slug-1", card1.getSlug());
        assertEquals("描述1", card1.getDescription());
        assertEquals(2, card1.getPostCount());
        assertFalse(card1.getFirstSummary().isBlank());

        SeriesCardDTO card2 = result.series().get(1);
        assertEquals(1, card2.getPostCount());
        assertFalse(card2.getFirstSummary().isBlank());
    }

    @Test
    void execute_seriesWithNoPosts_firstSummaryIsNull() {
        when(seriesRepository.findAll()).thenReturn(List.of(series(1L, "专栏A")));
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(List.of());

        ListSeriesQryExe.PageResult result = qryExe.execute(1);

        assertEquals(1, result.series().size());
        SeriesCardDTO card = result.series().get(0);
        assertEquals(0, card.getPostCount());
        assertNull(card.getFirstSummary());
    }

    @Test
    void execute_multiplePages_returnsCorrectPage() {
        List<Series> all = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            all.add(series(i, "专栏" + i));
        }
        when(seriesRepository.findAll()).thenReturn(all);
        // Only mock posts for the series on page 2 (id 11..20)
        for (long i = 11; i <= 20; i++) {
            when(seriesRepository.findPublishedPostsBySeries(i))
                    .thenReturn(List.of(post(i, 1, "内容" + i)));
        }

        ListSeriesQryExe.PageResult result = qryExe.execute(2);

        assertEquals(25, result.total());
        assertEquals(3, result.totalPages());
        assertEquals(2, result.currentPage());
        assertEquals(10, result.series().size());
        assertEquals(11L, result.series().get(0).getId());
        assertEquals(20L, result.series().get(9).getId());
    }

    @Test
    void execute_lastPage_returnsRemainingSeries() {
        List<Series> all = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            all.add(series(i, "专栏" + i));
        }
        when(seriesRepository.findAll()).thenReturn(all);
        for (long i = 21; i <= 25; i++) {
            when(seriesRepository.findPublishedPostsBySeries(i))
                    .thenReturn(List.of());
        }

        ListSeriesQryExe.PageResult result = qryExe.execute(3);

        assertEquals(5, result.series().size());
        assertEquals(21L, result.series().get(0).getId());
        assertEquals(25L, result.series().get(4).getId());
    }

    @Test
    void execute_pageBeyondRange_returnsEmptyList() {
        when(seriesRepository.findAll()).thenReturn(List.of(series(1L, "专栏A")));

        // page 2: from=10, total=1 => from >= total => List.of()
        ListSeriesQryExe.PageResult result = qryExe.execute(2);

        assertTrue(result.series().isEmpty());
        assertEquals(1, result.total());
        assertEquals(1, result.totalPages());
    }

    @Test
    void execute_emptySeries_returnsEmptyList() {
        when(seriesRepository.findAll()).thenReturn(List.of());

        ListSeriesQryExe.PageResult result = qryExe.execute(1);

        assertTrue(result.series().isEmpty());
        assertEquals(0, result.total());
        assertEquals(1, result.totalPages());
    }
}
