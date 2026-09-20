package manfred.bytedepth.app.series;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class SeriesNavigationQryExe {

    private static final Comparator<SeriesPostItemDTO> SERIES_ORDER =
            Comparator.comparing(SeriesPostItemDTO::getSeriesOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(SeriesPostItemDTO::getId, Comparator.nullsLast(Long::compareTo));

    public SeriesNavigation execute(Long seriesId, Long currentPostId, List<SeriesPostItemDTO> sourcePosts) {
        List<SeriesPostItemDTO> posts = sourcePosts == null ? List.of() : sourcePosts.stream()
                .sorted(SERIES_ORDER)
                .toList();
        int total = posts.size();
        int index = -1;
        for (int i = 0; i < total; i++) {
            if (currentPostId != null && currentPostId.equals(posts.get(i).getId())) {
                index = i;
                break;
            }
        }
        int position = index < 0 ? 0 : index + 1;
        int progressPercent = total == 0 || position == 0 ? 0 : Math.round(position * 100f / total);
        SeriesPostItemDTO previous = index > 0 ? posts.get(index - 1) : null;
        SeriesPostItemDTO next = index >= 0 && index + 1 < total ? posts.get(index + 1) : null;
        return new SeriesNavigation(posts, position, total, progressPercent, previous, next);
    }
}
