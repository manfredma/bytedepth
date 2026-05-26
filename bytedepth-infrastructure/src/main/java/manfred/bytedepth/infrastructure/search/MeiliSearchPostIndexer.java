package manfred.bytedepth.infrastructure.search;

import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.PostSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public MeiliSearchPostIndexer(
            @Value("${bytedepth.search.url}") String url,
            @Value("${bytedepth.search.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
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
    public List<PostSearchDoc> search(String query) {
        try {
            var response = restClient.get()
                    .uri("/indexes/{index}/search?q={q}&limit=20&attributesToHighlight=title,content&highlightPreTag=<em>&highlightPostTag=</em>",
                            INDEX, query)
                    .retrieve()
                    .body(Map.class);

            if (response == null) return List.of();

            var hits = (List<Map<String, Object>>) response.get("hits");
            if (hits == null) return List.of();

            return hits.stream().map(this::fromMap).toList();
        } catch (Exception e) {
            log.warn("MeiliSearch 搜索失败 q={}: {}", query, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> toMap(PostSearchDoc doc) {
        return Map.of(
                "id", doc.getId(),
                "title", doc.getTitle(),
                "content", truncate(doc.getContent(), 3000),
                "categoryName", doc.getCategoryName() != null ? doc.getCategoryName() : "",
                "categorySlug", doc.getCategorySlug() != null ? doc.getCategorySlug() : "",
                "tags", doc.getTags() != null ? doc.getTags() : List.of(),
                "seriesName", doc.getSeriesName() != null ? doc.getSeriesName() : ""
        );
    }

    @SuppressWarnings("unchecked")
    private PostSearchDoc fromMap(Map<String, Object> hit) {
        // MeiliSearch 把 highlight 放在 _formatted 子对象里
        Map<String, Object> formatted = hit.containsKey("_formatted")
                ? (Map<String, Object>) hit.get("_formatted")
                : hit;

        return PostSearchDoc.builder()
                .id(((Number) hit.get("id")).longValue())
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
