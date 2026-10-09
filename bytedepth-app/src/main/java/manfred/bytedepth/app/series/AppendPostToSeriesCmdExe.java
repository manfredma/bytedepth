package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppendPostToSeriesCmdExe {

    private final PostRepository postRepository;
    private final SeriesRepository seriesRepository;

    /** 将文章追加到专栏末尾，order = 当前最大 order + 1 */
    public void execute(Long postId, Long seriesId) {
        int maxOrder = seriesRepository.findMaxOrderInSeries(seriesId);
        postRepository.setPostSeries(postId, seriesId, maxOrder + 1);
    }
}
