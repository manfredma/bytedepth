package manfred.bytedepth.app.series;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.MarkdownTextExtractor;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetSeriesForPortalQryExe {

    private final SeriesRepository seriesRepository;

    private static final int PAGE_SIZE = 10;

    public SeriesPortalDTO execute(String slug, int page) {
        Series series = seriesRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("专栏不存在: " + slug));

        List<SeriesPostItem> allPosts = seriesRepository.findPublishedPostsBySeries(series.getId());
        long total = allPosts.size();
        long totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int from = (page - 1) * PAGE_SIZE;
        int to = (int) Math.min(from + PAGE_SIZE, total);
        List<SeriesPostItem> pagePosts = from < total ? allPosts.subList(from, to) : List.of();

        List<SeriesPortalPostDTO> postDTOs = pagePosts.stream().map(item -> {
            SeriesPortalPostDTO dto = new SeriesPortalPostDTO();
            dto.setId(item.id());
            dto.setSlug(item.slug());
            dto.setTitle(item.title());
            dto.setSeriesOrder(item.seriesOrder());
            dto.setSummary(MarkdownTextExtractor.excerpt(item.content(), 160));
            dto.setPublishedAt(item.publishedAt());
            return dto;
        }).collect(Collectors.toList());

        SeriesPortalDTO result = new SeriesPortalDTO();
        result.setId(series.getId());
        result.setName(series.getName());
        result.setSlug(series.getSlug());
        result.setDescription(series.getDescription());
        result.setPosts(postDTOs);
        result.setTotalPosts(total);
        result.setCurrentPage(page);
        result.setTotalPages(totalPages);
        return result;
    }

}
