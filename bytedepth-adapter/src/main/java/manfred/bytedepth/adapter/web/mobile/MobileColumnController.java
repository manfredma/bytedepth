package manfred.bytedepth.adapter.web.mobile;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.document.MobileContentService;
import manfred.bytedepth.app.series.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/v1/columns")
@RequiredArgsConstructor
public class MobileColumnController {
    private final ListSeriesQryExe series;
    private final GetSeriesForPortalQryExe detail;

    public record Card(String id, String name, String slug, String description, long postCount, String firstSummary) {}

    public record Post(String id, String slug, String title, Integer seriesOrder, String summary, String publishedAt) {}

    public record Column(String id, String name, String slug, String description, MobilePage<Post> posts) {}

    @GetMapping
    public MobilePage<Card> columns(
            @RequestParam(defaultValue = "1", name = "page") int page,
            @RequestParam(defaultValue = "10", name = "pageSize") int size) {
        MobilePage.fixed(page, size);
        var result = series.execute(page);
        return MobilePage.of(
                result.series().stream()
                        .map(c -> new Card(
                                c.getId().toString(),
                                c.getName(),
                                c.getSlug(),
                                c.getDescription(),
                                c.getPostCount(),
                                c.getFirstSummary()))
                        .toList(),
                page,
                size,
                result.total());
    }

    @GetMapping("/{id}")
    public Column column(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1", name = "page") int page,
            @RequestParam(defaultValue = "10", name = "pageSize") int size) {
        MobilePage.fixed(page, size);
        if (id < 1) throw new IllegalArgumentException("Invalid column ID");
        var column = detail.execute(id, page);
        return new Column(
                column.getId().toString(),
                column.getName(),
                column.getSlug(),
                column.getDescription(),
                MobilePage.of(
                        column.getPosts().stream()
                                .map(p -> new Post(
                                        p.getId().toString(),
                                        p.getSlug(),
                                        p.getTitle(),
                                        p.getSeriesOrder(),
                                        p.getSummary(),
                                        MobileContentService.utc(p.getPublishedAt())))
                                .toList(),
                        page,
                        size,
                        column.getTotalPosts()));
    }
}
