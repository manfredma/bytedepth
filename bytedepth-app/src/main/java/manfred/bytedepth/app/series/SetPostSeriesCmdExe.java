package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SetPostSeriesCmdExe {

    private final PostRepository postRepository;
    private final SeriesRepository seriesRepository;

    /**
     * 给文章设置专栏。seriesSlug 不存在时自动创建（name = slug）。
     */
    public void execute(Long postId, String seriesSlug, String seriesName, Integer seriesOrder, Long authorId) {
        Series series = seriesRepository.findBySlug(seriesSlug)
                .orElseGet(() -> seriesRepository.save(
                        Series.create(seriesName != null ? seriesName : seriesSlug, seriesSlug, null, authorId)
                ));
        postRepository.setPostSeries(postId, series.getId(), seriesOrder);
    }
}
