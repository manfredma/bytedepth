package manfred.bytedepth.app.series;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteSeriesCmdExe {
    private final SeriesRepository seriesRepository;
    private final IndexPostCmdExe indexPostCmdExe;

    public void execute(Long seriesId) {
        List<Long> affectedPostIds = seriesRepository.findAllPostsBySeries(seriesId).stream()
                .map(SeriesPostItem::id)
                .toList();
        seriesRepository.deleteWithPosts(seriesId);
        if (!affectedPostIds.isEmpty()) indexPostCmdExe.executeBatch(new java.util.ArrayList<>(affectedPostIds));
    }
}
