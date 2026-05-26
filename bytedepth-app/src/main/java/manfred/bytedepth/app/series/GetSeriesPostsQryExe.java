package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetSeriesPostsQryExe {

    private final SeriesRepository seriesRepository;

    public List<SeriesPostItemDTO> execute(Long seriesId) {
        return seriesRepository.findPublishedPostsBySeries(seriesId).stream()
                .map(item -> {
                    SeriesPostItemDTO dto = new SeriesPostItemDTO();
                    dto.setId(item.id());
                    dto.setTitle(item.title());
                    dto.setSeriesOrder(item.seriesOrder());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
