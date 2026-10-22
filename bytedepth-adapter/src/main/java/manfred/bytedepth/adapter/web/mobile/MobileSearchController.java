package manfred.bytedepth.adapter.web.mobile;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.SearchPostsQryExe;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/v1")
@RequiredArgsConstructor
public class MobileSearchController {
    private final SearchPostsQryExe search;
    private static final org.owasp.html.PolicyFactory TEXT = new org.owasp.html.HtmlPolicyBuilder().toFactory();

    public record Hit(
            String id,
            String slug,
            String title,
            String snippet,
            String categoryName,
            String categorySlug,
            List<String> tags,
            String seriesName) {}

    @GetMapping("/search")
    public MobilePage<Hit> search(
            @RequestParam("query") String query,
            @RequestParam(defaultValue = "1", name = "page") int page,
            @RequestParam(defaultValue = "10", name = "pageSize") int size) {
        MobilePage.fixed(page, size);
        String normalized = query.trim();
        if (normalized.isEmpty() || normalized.length() > 200) throw new IllegalArgumentException("Invalid query");
        var result = search.execute(normalized, page);
        return MobilePage.of(
                result.getHits().stream()
                        .map(h -> new Hit(
                                h.getId().toString(),
                                h.getSlug(),
                                plain(h.getTitle()),
                                plain(h.getContent()),
                                h.getCategoryName(),
                                h.getCategorySlug(),
                                h.getTags(),
                                h.getSeriesName()))
                        .toList(),
                page,
                size,
                result.getTotalHits());
    }

    private static String plain(String value) {
        return value == null
                ? ""
                : org.owasp.html.Encoding.decodeHtml(TEXT.sanitize(value)).trim();
    }
}
