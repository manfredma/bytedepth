package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MovePostInSeriesCmdExe {

    public enum Direction { UP, DOWN }

    private final PostRepository postRepository;
    private final SeriesRepository seriesRepository;

    public void execute(Long seriesId, Long postId, Direction direction) {
        List<SeriesPostItem> posts = seriesRepository.findAllPostsBySeries(seriesId);
        int idx = -1;
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).id().equals(postId)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;

        if (direction == Direction.UP && idx == 0) return;
        if (direction == Direction.DOWN && idx == posts.size() - 1) return;

        int swapIdx = direction == Direction.UP ? idx - 1 : idx + 1;
        SeriesPostItem current = posts.get(idx);
        SeriesPostItem swap = posts.get(swapIdx);

        postRepository.setPostSeries(current.id(), seriesId, swap.seriesOrder());
        postRepository.setPostSeries(swap.id(), seriesId, current.seriesOrder());
    }
}
