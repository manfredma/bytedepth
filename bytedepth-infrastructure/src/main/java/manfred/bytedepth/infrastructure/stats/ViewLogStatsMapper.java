package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.infrastructure.stats.dto.CountryViewStat;
import manfred.bytedepth.infrastructure.stats.dto.PostViewRank;
import manfred.bytedepth.infrastructure.stats.dto.TrendPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 访问日志聚合统计查询。所有 SQL 定义在 ViewLogStatsMapper.xml。
 * percent 字段由 SQL 固定为 0.0，由 AdminAnalyticsController 回填。
 */
@Mapper
public interface ViewLogStatsMapper {

    List<PostViewRank> topPosts(@Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                @Param("limit") int limit);

    List<CountryViewStat> countryStats(@Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    List<PostViewRank> countryTopPosts(@Param("country") String country,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("limit") int limit);

    List<TrendPoint> postTrend(@Param("postId") Long postId,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime,
                               @Param("format") String format);

    List<TrendPoint> overviewTrend(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("format") String format);
}
