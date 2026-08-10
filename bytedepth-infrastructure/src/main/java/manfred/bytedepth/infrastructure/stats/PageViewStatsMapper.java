package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.app.analytics.CountryViewStatDTO;
import manfred.bytedepth.app.analytics.PageViewRankDTO;
import manfred.bytedepth.app.analytics.TrendPointDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PageViewStatsMapper {

    List<PageViewRankDTO> topPages(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("limit") int limit);

    List<CountryViewStatDTO> pageCountryStats(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    List<PageViewRankDTO> countryTopPages(@Param("country") String country,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("limit") int limit);

    List<TrendPointDTO> pageTrend(@Param("pagePath") String pagePath,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("format") String format);

    List<TrendPointDTO> pageOverviewTrend(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("format") String format);
}