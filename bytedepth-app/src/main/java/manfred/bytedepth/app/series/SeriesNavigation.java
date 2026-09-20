package manfred.bytedepth.app.series;

import java.util.List;

public record SeriesNavigation(
        List<SeriesPostItemDTO> posts,
        int position,
        int total,
        int progressPercent,
        SeriesPostItemDTO previous,
        SeriesPostItemDTO next
) {
}
