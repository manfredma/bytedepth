package manfred.bytedepth.adapter.web.mobile;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import manfred.bytedepth.app.series.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MobileColumnControllerTest {
    final ListSeriesQryExe series = mock(ListSeriesQryExe.class);
    final GetSeriesForPortalQryExe detail = mock(GetSeriesForPortalQryExe.class);
    final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MobileColumnController(series, detail))
            .setControllerAdvice(new MobileExceptionHandler())
            .build();

    @Test
    void listUsesStringIdsAndUniformPage() throws Exception {
        var card = new SeriesCardDTO();
        card.setId(9007199254740993L);
        card.setName("Column");
        when(series.execute(1)).thenReturn(new ListSeriesQryExe.PageResult(List.of(card), 1, 1, 1));
        mvc.perform(get("/api/mobile/v1/columns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.pageSize").value(10));
    }

    @Test
    void nonfixedPaginationIsRejected() throws Exception {
        mvc.perform(get("/api/mobile/v1/columns?pageSize=9")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/mobile/v1/columns/7?page=0")).andExpect(status().isBadRequest());
    }

    @Test
    void idDetailUsesPublishedPortalQuery() throws Exception {
        var repo = mock(manfred.bytedepth.domain.series.SeriesRepository.class);
        when(repo.findById(7L))
                .thenReturn(java.util.Optional.of(
                        manfred.bytedepth.domain.series.Series.reconstruct(7L, "Column", "column", "Description")));
        when(repo.findPublishedPostsBySeries(7L))
                .thenReturn(List.of(new manfred.bytedepth.domain.series.SeriesPostItem(
                        9007199254740993L,
                        "published",
                        "Public",
                        2,
                        "**summary**",
                        "PUBLISHED",
                        java.time.LocalDateTime.of(2026, 10, 1, 8, 0))));
        var query = new GetSeriesForPortalQryExe(repo);
        org.assertj.core.api.Assertions.assertThat(query.execute(7L, 1)).isNotNull();
        var http = MockMvcBuilders.standaloneSetup(new MobileColumnController(series, query))
                .setControllerAdvice(new MobileExceptionHandler())
                .build();
        http.perform(get("/api/mobile/v1/columns/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.items[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.posts.items[0].publishedAt").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.posts.total").value(1));
        verify(repo, never()).findAllPostsBySeries(anyLong());
    }

    @Test
    void largePagesDoNotOverflowSeriesOffsets() {
        var repo = mock(manfred.bytedepth.domain.series.SeriesRepository.class);
        when(repo.findAll())
                .thenReturn(List.of(manfred.bytedepth.domain.series.Series.reconstruct(7L, "Column", "column", null)));
        org.assertj.core.api.Assertions.assertThatCode(() -> new ListSeriesQryExe(repo).execute(Integer.MAX_VALUE))
                .doesNotThrowAnyException();
    }

    @Test
    void missingColumnAndNegativeIdHaveExplicitErrors() throws Exception {
        var repo = mock(manfred.bytedepth.domain.series.SeriesRepository.class);
        var http = MockMvcBuilders.standaloneSetup(
                        new MobileColumnController(new ListSeriesQryExe(repo), new GetSeriesForPortalQryExe(repo)))
                .setControllerAdvice(new MobileExceptionHandler())
                .build();
        http.perform(get("/api/mobile/v1/columns/7")).andExpect(status().isNotFound());
        http.perform(get("/api/mobile/v1/columns/-1")).andExpect(status().isBadRequest());
    }

    @Test
    void sharedPortalAndListingPreservePublishedSummariesAndEmptyPages() {
        var repo = mock(manfred.bytedepth.domain.series.SeriesRepository.class);
        var column = manfred.bytedepth.domain.series.Series.reconstruct(7L, "Column", "column", null);
        when(repo.findById(7L)).thenReturn(java.util.Optional.of(column));
        when(repo.findBySlug("column")).thenReturn(java.util.Optional.of(column));
        when(repo.findAll())
                .thenReturn(List.of(
                        column, manfred.bytedepth.domain.series.Series.reconstruct(8L, "Empty", "empty", null)));
        when(repo.findPublishedPostsBySeries(7L))
                .thenReturn(List.of(new manfred.bytedepth.domain.series.SeriesPostItem(
                        1L, "slug", "Title", null, "**public**", "PUBLISHED", null)));
        var listing = new ListSeriesQryExe(repo).execute(1);
        org.assertj.core.api.Assertions.assertThat(listing.series())
                .extracting(SeriesCardDTO::getFirstSummary)
                .containsExactly("public", null);
        var portal = new GetSeriesForPortalQryExe(repo);
        org.assertj.core.api.Assertions.assertThat(portal.execute("column", 1).getPosts())
                .hasSize(1);
        org.assertj.core.api.Assertions.assertThat(
                        portal.execute(7L, Integer.MAX_VALUE).getPosts())
                .isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> portal.execute("missing", 1))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
