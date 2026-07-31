package manfred.bytedepth.infrastructure.series;

import manfred.bytedepth.domain.series.Series;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeriesRepositoryImplTest {

    private final SeriesMapper mapper = Mockito.mock(SeriesMapper.class);
    private final SeriesRepositoryImpl repository = new SeriesRepositoryImpl(mapper);

    @Test
    void save_insertsNewSeriesAndUpdatesExistingSeries() {
        when(mapper.insert(any())).thenAnswer(invocation -> {
            invocation.<SeriesDO>getArgument(0).setId(1L);
            return 1;
        });
        Series created = repository.save(Series.create("Java", "java", "基础", 7L));
        assertEquals(1L, created.getId());
        assertEquals(7L, created.getAuthorId());

        Series updated = repository.save(Series.reconstruct(1L, "Java", "java", "更新", 7L));
        assertEquals("更新", updated.getDescription());
        verify(mapper).updateById(any());
    }

    @Test
    void findMethods_mapExistingSeriesAndReturnEmptyForMissing() {
        SeriesDO row = seriesRow();
        when(mapper.selectOne(any())).thenReturn(row, null);
        when(mapper.selectById(1L)).thenReturn(row);
        when(mapper.selectById(2L)).thenReturn(null);

        assertEquals(7L, repository.findBySlug("java").orElseThrow().getAuthorId());
        assertFalse(repository.findBySlug("missing").isPresent());
        assertEquals("Java", repository.findById(1L).orElseThrow().getName());
        assertTrue(repository.findById(2L).isEmpty());
    }

    @Test
    void postQueries_mapPublishedAllAndCandidatePosts() {
        SeriesPostItemDO item = postRow();
        when(mapper.findPublishedPostsBySeries(3L)).thenReturn(List.of(item));
        when(mapper.findAllPostsBySeries(3L)).thenReturn(List.of(item));
        when(mapper.findCandidatesForSeries(3L, "java", 10, 10)).thenReturn(List.of(item));
        when(mapper.findCandidatesForSeriesByAuthor(3L, 7L, "java", 10, 10)).thenReturn(List.of(item));

        assertEquals("post", repository.findPublishedPostsBySeries(3L).getFirst().slug());
        assertEquals("内容", repository.findAllPostsBySeries(3L).getFirst().content());
        assertEquals(9L, repository.findCandidatesForSeries(3L, "java", 2, 10).getFirst().id());
        assertEquals("DRAFT", repository.findCandidatesForSeriesByAuthor(3L, 7L, "java", 2, 10).getFirst().status());
    }

    @Test
    void candidateCountsAndMaximumOrder_delegateToMapper() {
        when(mapper.countCandidatesForSeries(3L, "java")).thenReturn(4L);
        when(mapper.countCandidatesForSeriesByAuthor(3L, 7L, "java")).thenReturn(2L);
        when(mapper.findMaxOrderInSeries(3L)).thenReturn(8);

        assertEquals(4L, repository.countCandidatesForSeries(3L, "java"));
        assertEquals(2L, repository.countCandidatesForSeriesByAuthor(3L, 7L, "java"));
        assertEquals(8, repository.findMaxOrderInSeries(3L));
    }

    @Test
    void deleteWithPosts_clearsPostAssociationsBeforeDeletingSeries() {
        repository.deleteWithPosts(3L);

        var order = Mockito.inOrder(mapper);
        order.verify(mapper).clearAllPostsInSeries(3L);
        order.verify(mapper).deleteById(3L);
    }

    @Test
    void listMethods_mapAllSeriesAndAuthorSeries() {
        SeriesDO row = seriesRow();
        when(mapper.selectList(any())).thenReturn(List.of(row));

        assertEquals("Java", repository.findAll().getFirst().getName());
        assertEquals(7L, repository.findByAuthorId(7L).getFirst().getAuthorId());
    }

    private SeriesDO seriesRow() {
        SeriesDO row = new SeriesDO();
        row.setId(3L);
        row.setName("Java");
        row.setSlug("java");
        row.setDescription("基础");
        row.setAuthorId(7L);
        return row;
    }

    private SeriesPostItemDO postRow() {
        SeriesPostItemDO row = new SeriesPostItemDO();
        row.setId(9L);
        row.setSlug("post");
        row.setTitle("标题");
        row.setSeriesOrder(2);
        row.setContent("内容");
        row.setStatus("DRAFT");
        row.setPublishedAt(LocalDateTime.now());
        return row;
    }
}
