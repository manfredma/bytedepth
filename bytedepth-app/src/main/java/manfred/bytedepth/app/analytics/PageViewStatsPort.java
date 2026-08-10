package manfred.bytedepth.app.analytics;

import java.time.LocalDateTime;
import java.util.List;

/** 页面访问统计查询端口。 */
public interface PageViewStatsPort {

    List<PageViewRankDTO> topPages(LocalDateTime startTime, LocalDateTime endTime, int limit);

    List<CountryViewStatDTO> pageCountryStats(LocalDateTime startTime, LocalDateTime endTime);

    List<PageViewRankDTO> countryTopPages(String country, LocalDateTime startTime, LocalDateTime endTime, int limit);

    List<TrendPointDTO> pageTrend(String pagePath, LocalDateTime startTime, LocalDateTime endTime, String format);

    List<TrendPointDTO> pageOverviewTrend(LocalDateTime startTime, LocalDateTime endTime, String format);
}