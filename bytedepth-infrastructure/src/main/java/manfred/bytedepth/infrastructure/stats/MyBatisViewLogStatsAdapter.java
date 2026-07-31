package manfred.bytedepth.infrastructure.stats;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.analytics.CountryViewStatDTO;
import manfred.bytedepth.app.analytics.PostViewRankDTO;
import manfred.bytedepth.app.analytics.TrendPointDTO;
import manfred.bytedepth.app.analytics.ViewLogStatsPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MyBatisViewLogStatsAdapter implements ViewLogStatsPort {
    private final ViewLogStatsMapper mapper;
    @Override public List<PostViewRankDTO> topPosts(LocalDateTime start, LocalDateTime end, int limit) { return mapper.topPosts(start, end, limit); }
    @Override public List<CountryViewStatDTO> countryStats(LocalDateTime start, LocalDateTime end) { return mapper.countryStats(start, end); }
    @Override public List<PostViewRankDTO> countryTopPosts(String country, LocalDateTime start, LocalDateTime end, int limit) { return mapper.countryTopPosts(country, start, end, limit); }
    @Override public List<TrendPointDTO> postTrend(Long postId, LocalDateTime start, LocalDateTime end, String format) { return mapper.postTrend(postId, start, end, format); }
    @Override public List<TrendPointDTO> overviewTrend(LocalDateTime start, LocalDateTime end, String format) { return mapper.overviewTrend(start, end, format); }
}
