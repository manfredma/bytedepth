package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.infrastructure.series.SeriesMapper;
import manfred.bytedepth.infrastructure.series.SeriesPostItemDO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetSeriesPostsQryExe {

    private final SeriesMapper seriesMapper;

    public List<SeriesPostItemDTO> execute(Long seriesId) {
        return seriesMapper.findPublishedPostsBySeries(seriesId).stream()
                .map(d -> {
                    SeriesPostItemDTO dto = new SeriesPostItemDTO();
                    dto.setId(d.getId());
                    dto.setTitle(d.getTitle());
                    dto.setSeriesOrder(d.getSeriesOrder());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
