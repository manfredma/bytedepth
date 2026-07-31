package manfred.bytedepth.app.analytics;

import java.time.LocalDateTime;
import java.util.List;

public interface ViewLogStatsPort {
    List<PostViewRankDTO> topPosts(LocalDateTime startTime, LocalDateTime endTime, int limit);
    List<CountryViewStatDTO> countryStats(LocalDateTime startTime, LocalDateTime endTime);
    List<PostViewRankDTO> countryTopPosts(String country, LocalDateTime startTime, LocalDateTime endTime, int limit);
    List<TrendPointDTO> postTrend(Long postId, LocalDateTime startTime, LocalDateTime endTime, String format);
    List<TrendPointDTO> overviewTrend(LocalDateTime startTime, LocalDateTime endTime, String format);
}
