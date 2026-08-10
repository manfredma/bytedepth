package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.app.analytics.CountryViewStatDTO;
import manfred.bytedepth.app.analytics.PageViewRankDTO;
import manfred.bytedepth.app.analytics.TrendPointDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisPageViewStatsAdapterTest {

    @Mock
    private PageViewStatsMapper mapper;

    @InjectMocks
    private MyBatisPageViewStatsAdapter adapter;

    @Test
    void topPages_delegatesToMapper() {
        var dto = new PageViewRankDTO();
        dto.setPagePath("/"); dto.setViewCount(10);
        when(mapper.topPages(any(), any(), anyInt())).thenReturn(List.of(dto));

        List<PageViewRankDTO> result = adapter.topPages(
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPagePath()).isEqualTo("/");
        verify(mapper).topPages(any(), any(), eq(20));
    }

    @Test
    void pageCountryStats_delegatesToMapper() {
        var dto = new CountryViewStatDTO();
        dto.setCountry("中国"); dto.setViewCount(10);
        when(mapper.pageCountryStats(any(), any())).thenReturn(List.of(dto));

        List<CountryViewStatDTO> result = adapter.pageCountryStats(
                LocalDateTime.now().minusDays(7), LocalDateTime.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCountry()).isEqualTo("中国");
        verify(mapper).pageCountryStats(any(), any());
    }

    @Test
    void countryTopPages_delegatesToMapper() {
        var dto = new PageViewRankDTO();
        dto.setPagePath("/about"); dto.setViewCount(5);
        when(mapper.countryTopPages(any(), any(), any(), anyInt())).thenReturn(List.of(dto));

        List<PageViewRankDTO> result = adapter.countryTopPages("中国",
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPagePath()).isEqualTo("/about");
        verify(mapper).countryTopPages(eq("中国"), any(), any(), eq(20));
    }

    @Test
    void pageTrend_delegatesToMapper() {
        var dto = new TrendPointDTO();
        dto.setLabel("08-01"); dto.setViewCount(3);
        when(mapper.pageTrend(any(), any(), any(), any())).thenReturn(List.of(dto));

        List<TrendPointDTO> result = adapter.pageTrend("/about",
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), "%m-%d");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("08-01");
        verify(mapper).pageTrend(eq("/about"), any(), any(), eq("%m-%d"));
    }

    @Test
    void pageOverviewTrend_delegatesToMapper() {
        var dto = new TrendPointDTO();
        dto.setLabel("08-01"); dto.setViewCount(5);
        when(mapper.pageOverviewTrend(any(), any(), any())).thenReturn(List.of(dto));

        List<TrendPointDTO> result = adapter.pageOverviewTrend(
                LocalDateTime.now().minusDays(7), LocalDateTime.now(), "%m-%d");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("08-01");
        verify(mapper).pageOverviewTrend(any(), any(), eq("%m-%d"));
    }
}