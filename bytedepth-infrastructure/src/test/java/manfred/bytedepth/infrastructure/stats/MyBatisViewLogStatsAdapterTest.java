package manfred.bytedepth.infrastructure.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import manfred.bytedepth.app.analytics.CountryViewStatDTO;
import manfred.bytedepth.app.analytics.PostViewRankDTO;
import manfred.bytedepth.app.analytics.TrendPointDTO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MyBatisViewLogStatsAdapterTest {

    @Test
    void delegatesEveryStatisticsQueryToMapper() {
        ViewLogStatsMapper mapper = Mockito.mock(ViewLogStatsMapper.class);
        MyBatisViewLogStatsAdapter adapter = new MyBatisViewLogStatsAdapter(mapper);
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        List<PostViewRankDTO> postRanks = List.of(new PostViewRankDTO());
        List<CountryViewStatDTO> countries = List.of(new CountryViewStatDTO());
        List<TrendPointDTO> trend = List.of(new TrendPointDTO());
        when(mapper.topPosts(start, end, 10)).thenReturn(postRanks);
        when(mapper.countryStats(start, end)).thenReturn(countries);
        when(mapper.countryTopPosts("CN", start, end, 10)).thenReturn(postRanks);
        when(mapper.postTrend(1L, start, end, "%m-%d")).thenReturn(trend);
        when(mapper.overviewTrend(start, end, "%m-%d")).thenReturn(trend);

        assertEquals(postRanks, adapter.topPosts(start, end, 10));
        assertEquals(countries, adapter.countryStats(start, end));
        assertEquals(postRanks, adapter.countryTopPosts("CN", start, end, 10));
        assertEquals(trend, adapter.postTrend(1L, start, end, "%m-%d"));
        assertEquals(trend, adapter.overviewTrend(start, end, "%m-%d"));
    }
}
