package manfred.bytedepth.app.search;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.PostSearchPort;
import manfred.bytedepth.domain.search.SearchResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SearchPostsQryExe {

    private static final int DEFAULT_SIZE = 10;
    private final PostSearchPort postSearchPort;
    private final PostRepository postRepository;

    public SearchResult execute(String query, int page) {
        if (query == null || query.isBlank()) return new SearchResult(List.of(), 0, page, DEFAULT_SIZE);
        SearchResult result = postSearchPort.search(query.trim(), Math.max(1, page), DEFAULT_SIZE);
        // The index can lag or a write can fail after the database commit. Never expose
        // unpublished content or generate a link from an obsolete slug in that interval.
        List<PostSearchDoc> hits = result.getHits().stream()
                .flatMap(hit -> postRepository
                        .findById(hit.getId())
                        .filter(post -> post.getStatus() == PostStatus.PUBLISHED)
                        .map(post -> PostSearchDoc.builder()
                                .id(hit.getId())
                                .slug(post.getSlug())
                                .title(hit.getTitle())
                                .content(hit.getContent())
                                .categoryName(hit.getCategoryName())
                                .categorySlug(hit.getCategorySlug())
                                .tags(hit.getTags())
                                .seriesName(hit.getSeriesName())
                                .build())
                        .stream())
                .toList();
        long removed = result.getHits().size() - hits.size();
        return new SearchResult(hits, Math.max(0, result.getTotalHits() - removed), result.getPage(), result.getSize());
    }
}
