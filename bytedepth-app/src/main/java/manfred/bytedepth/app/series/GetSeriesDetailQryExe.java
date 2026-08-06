package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetSeriesDetailQryExe {

    private final SeriesRepository seriesRepository;

    public SeriesDetailDTO execute(String slug) {
        Series series = seriesRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("专栏不存在: " + slug));
        List<SeriesDetailPostDTO> posts = seriesRepository.findAllPostsBySeries(series.getId())
                .stream()
                .map(item -> {
                    SeriesDetailPostDTO dto = new SeriesDetailPostDTO();
                    dto.setId(item.id());
                    dto.setTitle(item.title());
                    dto.setSeriesOrder(item.seriesOrder());
                    dto.setStatus(item.status() != null ? item.status() : "");
                    return dto;
                })
                .collect(Collectors.toList());

        SeriesDetailDTO dto = new SeriesDetailDTO();
        dto.setId(series.getId());
        dto.setName(series.getName());
        dto.setSlug(series.getSlug());
        dto.setDescription(series.getDescription());
        dto.setPosts(posts);
        return dto;
    }
}
