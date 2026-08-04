package manfred.bytedepth.infrastructure.search;

import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.PostSearchPort;
import manfred.bytedepth.domain.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class MeiliSearchPostIndexer implements PostSearchPort {

    private static final Logger log = LoggerFactory.getLogger(MeiliSearchPostIndexer.class);
    private static final String INDEX = "posts";

    private final RestClient restClient;

    @Autowired
    public MeiliSearchPostIndexer(
            @Value("${bytedepth.search.url}") String url,
            @Value("${bytedepth.search.api-key}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build());
    }

    /** 测试专用构造器：注入已构造的 RestClient，便于单元测试 mock。 */
    MeiliSearchPostIndexer(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void index(PostSearchDoc doc) {
        try {
            restClient.post()
                    .uri("/indexes/{index}/documents", INDEX)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(toMap(doc)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("MeiliSearch 索引失败 postId={}: {}", doc.getId(), e.getMessage());
        }
    }

    @Override
    public void delete(Long postId) {
        try {
            restClient.delete()
                    .uri("/indexes/{index}/documents/{id}", INDEX, postId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("MeiliSearch 删除索引失败 postId={}: {}", postId, e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public SearchResult search(String query, int page, int size) {
        int offset = (page - 1) * size;
        try {
            var response = restClient.get()
                    .uri("/indexes/{index}/search?q={q}&limit={limit}&offset={offset}&attributesToHighlight=title,content&attributesToCrop=content:240&highlightPreTag=<em>&highlightPostTag=</em>",
                            INDEX, query, size, offset)
                    .retrieve()
                    .body(Map.class);

            if (response == null) return new SearchResult(List.of(), 0, page, size);

            var hits = (List<Map<String, Object>>) response.get("hits");
            if (hits == null) return new SearchResult(List.of(), 0, page, size);

            long total = response.get("estimatedTotalHits") instanceof Number n
                    ? n.longValue() : hits.size();

            return new SearchResult(hits.stream().map(this::fromMap).toList(), total, page, size);
        } catch (Exception e) {
            log.warn("MeiliSearch 搜索失败 q={}: {}", query, e.getMessage());
            return new SearchResult(List.of(), 0, page, size);
        }
    }

    private Map<String, Object> toMap(PostSearchDoc doc) {
        // Map.of 最多 10 个 key；用 java.util.HashMap 以便扩展
        var m = new java.util.HashMap<String, Object>();
        m.put("id", doc.getId());
        m.put("slug", doc.getSlug() != null ? doc.getSlug() : "");
        m.put("title", doc.getTitle());
        m.put("content", truncate(doc.getContent(), 3000));
        m.put("categoryName", doc.getCategoryName() != null ? doc.getCategoryName() : "");
        m.put("categorySlug", doc.getCategorySlug() != null ? doc.getCategorySlug() : "");
        m.put("tags", doc.getTags() != null ? doc.getTags() : List.of());
        m.put("seriesName", doc.getSeriesName() != null ? doc.getSeriesName() : "");
        return m;
    }

    @SuppressWarnings("unchecked")
    private PostSearchDoc fromMap(Map<String, Object> hit) {
        // MeiliSearch 把 highlight 放在 _formatted 子对象里
        Map<String, Object> formatted = hit.containsKey("_formatted")
                ? (Map<String, Object>) hit.get("_formatted")
                : hit;

        long id = ((Number) hit.get("id")).longValue();
        // 旧索引文档可能缺少 slug 字段（在 slug 特性上线前已索引）；
        // 回退到数字 ID，PostController 会 301 跳转到正确的 slug URL。
        String slug = str(hit.get("slug"));
        if (slug.isBlank()) {
            slug = String.valueOf(id);
        }

        return PostSearchDoc.builder()
                .id(id)
                .slug(slug)
                .title(str(formatted.get("title")))
                .content(str(formatted.get("content")))
                .categoryName(str(hit.get("categoryName")))
                .categorySlug(str(hit.get("categorySlug")))
                .tags(hit.get("tags") instanceof List ? (List<String>) hit.get("tags") : List.of())
                .seriesName(str(hit.get("seriesName")))
                .build();
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
