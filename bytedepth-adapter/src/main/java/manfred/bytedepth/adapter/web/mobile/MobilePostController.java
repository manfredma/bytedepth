package manfred.bytedepth.adapter.web.mobile;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.post.MarkdownTextExtractor;
import manfred.bytedepth.app.post.document.MobileContentService;
import manfred.bytedepth.app.post.query.*;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/v1")
@RequiredArgsConstructor
public class MobilePostController {
    private final ListPostsQryExe posts;
    private final PublicPostAccess access;
    private final ListCategoriesQryExe categories;
    private final ListTagsQryExe tags;

    public record Summary(
            String id,
            String slug,
            String title,
            String summary,
            String publishedAt,
            int estimatedReadingMinutes,
            MobileContentService.Category category) {}

    public record Category(String id, String name, String slug, String parentId, String parentName, int depth) {}

    public record Tag(String id, String name, String slug) {}

    public record Discovery(List<Summary> featured, List<Summary> latest, List<Category> categories, List<Tag> tags) {}

    @GetMapping("/posts")
    public MobilePage<Summary> posts(
            @RequestParam(defaultValue = "1", name = "page") int page,
            @RequestParam(defaultValue = "10", name = "pageSize") int size,
            @RequestParam(required = false, name = "tag") String tag,
            @RequestParam(required = false, name = "category") String category) {
        MobilePage.validate(page, size);
        boolean tagged = tag != null && !tag.isBlank();
        boolean categorized = category != null && !category.isBlank();
        if (tagged && categorized) throw new IllegalArgumentException("Choose one filter");
        if (tagged)
            return MobilePage.of(summaries(posts.executeByTag(tag, page, size)), page, size, posts.countByTag(tag));
        if (categorized)
            return MobilePage.of(
                    summaries(posts.executeByCategory(category, page, size)),
                    page,
                    size,
                    posts.countByCategory(category));
        return MobilePage.of(summaries(posts.execute(page, size)), page, size, posts.countPublished());
    }

    @GetMapping("/discovery")
    public Discovery discovery() {
        return new Discovery(
                summaries(posts.executeFeatured()),
                summaries(posts.execute(1, 10)),
                categories.execute().stream()
                        .map(c -> new Category(
                                c.getId().toString(),
                                c.getName(),
                                c.getSlug(),
                                c.getParentId() == null ? null : c.getParentId().toString(),
                                c.getParentName(),
                                c.getDepth()))
                        .toList(),
                tags.execute().stream()
                        .map(t -> new Tag(t.getId().toString(), t.getName(), t.getSlug()))
                        .toList());
    }

    @GetMapping("/posts/resolve")
    public PublicPostAccess.ResolvedPost resolve(@RequestParam("slug") String slug) {
        if (slug.isBlank()) throw new IllegalArgumentException("Slug required");
        return access.resolve(slug);
    }

    private static List<Summary> summaries(List<PostDTO> source) {
        return source.stream()
                .map(p -> new Summary(
                        p.getId().toString(),
                        p.getSlug(),
                        p.getTitle(),
                        MarkdownTextExtractor.excerpt(p.getContent(), 160),
                        MobileContentService.utc(p.getPublishedAt()),
                        MarkdownTextExtractor.estimatedReadingMinutes(p.getContent()),
                        p.getCategoryId() == null
                                ? null
                                : new MobileContentService.Category(
                                        p.getCategoryId().toString(), p.getCategoryName(), p.getCategorySlug())))
                .toList();
    }
}
