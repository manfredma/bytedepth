package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.MarkdownTextExtractor;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListSeriesQryExe {

    private final SeriesRepository seriesRepository;

    private static final int PAGE_SIZE = 10;

    public record PageResult(List<SeriesCardDTO> series, long total, int currentPage, long totalPages) {}

    public PageResult execute(int page) {
        List<Series> all = seriesRepository.findAll(); // 已按 name ASC
        long total = all.size();
        long totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = (page - 1) * PAGE_SIZE;
        int to = (int) Math.min(from + PAGE_SIZE, total);
        List<Series> pageSeries = from < total ? all.subList(from, to) : List.of();

        List<SeriesCardDTO> cards = pageSeries.stream().map(s -> {
            List<SeriesPostItem> posts = seriesRepository.findPublishedPostsBySeries(s.getId());
            SeriesCardDTO card = new SeriesCardDTO();
            card.setId(s.getId());
            card.setName(s.getName());
            card.setSlug(s.getSlug());
            card.setDescription(s.getDescription());
            card.setPostCount(posts.size());
            card.setFirstSummary(posts.isEmpty() ? null : MarkdownTextExtractor.excerpt(posts.get(0).content(), 160));
            return card;
        }).collect(Collectors.toList());

        return new PageResult(cards, total, page, totalPages);
    }

}
