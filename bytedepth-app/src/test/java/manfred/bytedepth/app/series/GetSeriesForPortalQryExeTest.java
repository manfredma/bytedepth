package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSeriesForPortalQryExeTest {

    @Mock
    private SeriesRepository seriesRepository;

    private GetSeriesForPortalQryExe qryExe;

    @BeforeEach
    void setUp() {
        qryExe = new GetSeriesForPortalQryExe(seriesRepository);
    }

    private Series series() {
        return Series.reconstruct(1L, "专栏A", "series-a", "描述");
    }

    private SeriesPostItem post(long id, int order, String content) {
        return new SeriesPostItem(id, "slug-" + id, "文章" + id, order, content, "PUBLISHED",
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    @Test
    void execute_slugNotFound_throwsNoSuchElementException() {
        when(seriesRepository.findBySlug("missing")).thenReturn(Optional.empty());

        var ex = assertThrows(NoSuchElementException.class, () -> qryExe.execute("missing", 1));
        assertTrue(ex.getMessage().contains("专栏不存在"));
    }

    @Test
    void execute_singlePage_returnsAllPosts() {
        when(seriesRepository.findBySlug("series-a")).thenReturn(Optional.of(series()));
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(List.of(
                post(10L, 1, "这是第一篇文章的正文内容，足够长可以生成摘要。"),
                post(20L, 2, "第二篇文章内容。")
        ));

        SeriesPortalDTO result = qryExe.execute("series-a", 1);

        assertEquals(1L, result.getId());
        assertEquals("专栏A", result.getName());
        assertEquals("series-a", result.getSlug());
        assertEquals("描述", result.getDescription());
        assertEquals(2, result.getTotalPosts());
        assertEquals(1, result.getCurrentPage());
        assertEquals(1, result.getTotalPages());
        assertEquals(2, result.getPosts().size());

        SeriesPortalPostDTO first = result.getPosts().get(0);
        assertEquals(10L, first.getId());
        assertEquals("slug-10", first.getSlug());
        assertEquals("文章10", first.getTitle());
        assertEquals(1, first.getSeriesOrder());
        assertTrue(first.getSummary() != null && !first.getSummary().isEmpty());
        assertTrue(first.getPublishedAt() != null);
    }

    @Test
    void execute_multiplePages_returnsCorrectPage() {
        List<SeriesPostItem> all = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            all.add(post(i, i, "文章内容" + i));
        }
        when(seriesRepository.findBySlug("series-a")).thenReturn(Optional.of(series()));
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(all);

        // page 2: items 10..19 (0-indexed from=10, to=20)
        SeriesPortalDTO result = qryExe.execute("series-a", 2);

        assertEquals(25, result.getTotalPosts());
        assertEquals(3, result.getTotalPages());
        assertEquals(2, result.getCurrentPage());
        assertEquals(10, result.getPosts().size());
        assertEquals(11L, result.getPosts().get(0).getId());
        assertEquals(20L, result.getPosts().get(9).getId());
    }

    @Test
    void execute_lastPage_returnsRemainingPosts() {
        List<SeriesPostItem> all = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            all.add(post(i, i, "文章内容" + i));
        }
        when(seriesRepository.findBySlug("series-a")).thenReturn(Optional.of(series()));
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(all);

        // page 3: from=20, to=25 => 5 items
        SeriesPortalDTO result = qryExe.execute("series-a", 3);

        assertEquals(5, result.getPosts().size());
        assertEquals(21L, result.getPosts().get(0).getId());
        assertEquals(25L, result.getPosts().get(4).getId());
    }

    @Test
    void execute_pageBeyondRange_returnsEmptyList() {
        List<SeriesPostItem> all = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            all.add(post(i, i, "文章内容" + i));
        }
        when(seriesRepository.findBySlug("series-a")).thenReturn(Optional.of(series()));
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(all);

        // page 2: from=10, total=5 => from >= total => List.of()
        SeriesPortalDTO result = qryExe.execute("series-a", 2);

        assertTrue(result.getPosts().isEmpty());
        assertEquals(5, result.getTotalPosts());
        assertEquals(1, result.getTotalPages());
    }

    @Test
    void execute_noPosts_returnsEmptyListAndOneTotalPage() {
        when(seriesRepository.findBySlug("series-a")).thenReturn(Optional.of(series()));
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(List.of());

        SeriesPortalDTO result = qryExe.execute("series-a", 1);

        assertTrue(result.getPosts().isEmpty());
        assertEquals(0, result.getTotalPosts());
        assertEquals(1, result.getTotalPages());
    }

    @Test
    void execute_exactlyPageSize_boundaryHandled() {
        List<SeriesPostItem> all = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            all.add(post(i, i, "文章内容" + i));
        }
        when(seriesRepository.findBySlug("series-a")).thenReturn(Optional.of(series()));
        when(seriesRepository.findPublishedPostsBySeries(1L)).thenReturn(all);

        // page 1: from=0, to=10 => all 10
        SeriesPortalDTO page1 = qryExe.execute("series-a", 1);
        assertEquals(10, page1.getPosts().size());

        // page 2: from=10, total=10 => from >= total => empty
        SeriesPortalDTO page2 = qryExe.execute("series-a", 2);
        assertTrue(page2.getPosts().isEmpty());
    }
}
