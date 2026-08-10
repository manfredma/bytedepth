package manfred.bytedepth.infrastructure.stats;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.analytics.CountryViewStatDTO;
import manfred.bytedepth.app.analytics.PageViewRankDTO;
import manfred.bytedepth.app.analytics.PageViewStatsPort;
import manfred.bytedepth.app.analytics.TrendPointDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MyBatisPageViewStatsAdapter implements PageViewStatsPort {

    private final PageViewStatsMapper mapper;

    @Override
    public List<PageViewRankDTO> topPages(LocalDateTime start, LocalDateTime end, int limit) {
        return mapper.topPages(start, end, limit);
    }

    @Override
    public List<CountryViewStatDTO> pageCountryStats(LocalDateTime start, LocalDateTime end) {
        return mapper.pageCountryStats(start, end);
    }

    @Override
    public List<PageViewRankDTO> countryTopPages(String country, LocalDateTime start, LocalDateTime end, int limit) {
        return mapper.countryTopPages(country, start, end, limit);
    }

    @Override
    public List<TrendPointDTO> pageTrend(String pagePath, LocalDateTime start, LocalDateTime end, String format) {
        return mapper.pageTrend(pagePath, start, end, format);
    }

    @Override
    public List<TrendPointDTO> pageOverviewTrend(LocalDateTime start, LocalDateTime end, String format) {
        return mapper.pageOverviewTrend(start, end, format);
    }
}