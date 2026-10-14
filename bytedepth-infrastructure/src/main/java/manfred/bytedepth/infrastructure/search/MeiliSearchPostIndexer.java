package manfred.bytedepth.infrastructure.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.PostSearchPort;
import manfred.bytedepth.domain.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MeiliSearchPostIndexer implements PostSearchPort {

    private static final Logger log = LoggerFactory.getLogger(MeiliSearchPostIndexer.class);
    private final RestClient restClient;
    private static final int DOCUMENT_PAGE_SIZE = 1000;
    private final String index;
    private final Duration taskTimeout;
    private final Duration pollInterval;

    @Autowired
    public MeiliSearchPostIndexer(
            @Value("${bytedepth.search.url}") String url,
            @Value("${bytedepth.search.api-key}") String apiKey,
            @Value("${bytedepth.search.index:posts}") String index) {
        this(
                RestClient.builder()
                        .baseUrl(url)
                        .requestFactory(requestFactory())
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                index);
    }

    /** 测试专用构造器：注入已构造的 RestClient，便于单元测试 mock。 */
    MeiliSearchPostIndexer(RestClient restClient) {
        this(restClient, "posts");
    }

    MeiliSearchPostIndexer(RestClient restClient, String index) {
        this(restClient, index, Duration.ofSeconds(30), Duration.ofMillis(100));
    }

    MeiliSearchPostIndexer(RestClient restClient, String index, Duration taskTimeout, Duration pollInterval) {
        this.restClient = restClient;
        this.index = index;
        this.taskTimeout = taskTimeout;
        this.pollInterval = pollInterval;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Override
    public List<Long> findIndexedIds() {
        var ids = new ArrayList<Long>();
        long offset = 0;
        long total;
        // Finish enumeration before reconciliation deletes anything: offsets shift after deletion.
        do {
            Map<?, ?> response;
            try {
                response = restClient
                        .get()
                        .uri(
                                "/indexes/{index}/documents?fields=id&limit={limit}&offset={offset}",
                                index,
                                DOCUMENT_PAGE_SIZE,
                                offset)
                        .retrieve()
                        .body(Map.class);
            } catch (HttpClientErrorException.NotFound e) {
                if (isMissingIndex(e)) return List.of();
                throw e;
            }
            var page = requireResponse(response);
            total = requireNonNegativeLong(page.get("total"));
            if (requireNonNegativeLong(page.get("offset")) != offset
                    || requireNonNegativeLong(page.get("limit")) != DOCUMENT_PAGE_SIZE) {
                throw new IllegalStateException("Unexpected Meilisearch document pagination");
            }
            if (!(page.get("results") instanceof List<?> documents)) {
                throw new IllegalStateException("Missing Meilisearch document results");
            }
            if (documents.isEmpty() && offset < total) {
                throw new IllegalStateException("Incomplete Meilisearch document page");
            }
            for (Object document : documents) {
                if (!(document instanceof Map<?, ?> fields)) {
                    throw new IllegalStateException("Malformed Meilisearch document");
                }
                ids.add(requireNonNegativeLong(fields.get("id")));
            }
            offset += documents.size();
            if (offset > total) throw new IllegalStateException("Inconsistent Meilisearch document total");
        } while (offset < total);
        return List.copyOf(ids);
    }

    private boolean isMissingIndex(HttpClientErrorException.NotFound error) {
        try {
            Map<?, ?> body = error.getResponseBodyAs(Map.class);
            return body != null && "index_not_found".equals(body.get("code"));
        } catch (RestClientException invalidBody) {
            return false;
        }
    }

    @Override
    public void index(PostSearchDoc doc) {
        indexAll(List.of(doc));
    }

    @Override
    public void indexAll(List<PostSearchDoc> docs) {
        if (docs.isEmpty()) return;
        ensureNotInterrupted();
        awaitTask(
                restClient
                        .post()
                        .uri("/indexes/{index}/documents", index)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(docs.stream().map(this::toMap).toList())
                        .retrieve()
                        .body(Map.class),
                false);
    }

    @Override
    public void delete(Long postId) {
        submitDeletion(() -> restClient
                .delete()
                .uri("/indexes/{index}/documents/{id}", index, postId)
                .retrieve()
                .body(Map.class));
    }

    @Override
    public void deleteAll(List<Long> ids) {
        if (ids.isEmpty()) return;
        submitDeletion(() -> restClient
                .post()
                .uri("/indexes/{index}/documents/delete-batch", index)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ids)
                .retrieve()
                .body(Map.class));
    }

    private void submitDeletion(java.util.function.Supplier<Map<?, ?>> submission) {
        ensureNotInterrupted();
        Map<?, ?> acceptance;
        try {
            acceptance = submission.get();
        } catch (HttpClientErrorException.NotFound e) {
            if (isMissingIndex(e)) return;
            throw e;
        }
        awaitTask(acceptance, true);
    }

    private static void ensureNotInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Interrupted before submitting Meilisearch write");
        }
    }

    private void awaitTask(Map<?, ?> acceptance, boolean deletion) {
        long uid = requireNonNegativeLong(requireResponse(acceptance).get("taskUid"));
        long started = System.nanoTime();
        // Each HTTP call is independently bounded; a deadline may overrun by one read timeout.
        while (System.nanoTime() - started < taskTimeout.toNanos()) {
            Map<?, ?> task = requireResponse(
                    restClient.get().uri("/tasks/{uid}", uid).retrieve().body(Map.class));
            Object status = task.get("status");
            if (!(status instanceof String state)) {
                throw new IllegalStateException("Missing Meilisearch task status uid=" + uid);
            }
            switch (state) {
                case "succeeded":
                    return;
                case "failed", "canceled":
                    // The task endpoint can fail asynchronously even when DELETE itself was accepted.
                    if (deletion
                            && "failed".equals(state)
                            && task.get("error") instanceof Map<?, ?> error
                            && "index_not_found".equals(error.get("code"))) return;
                    throw new IllegalStateException(
                            "Meilisearch task uid=" + uid + " " + state + ": " + task.get("error"));
                case "enqueued", "processing":
                    break;
                default:
                    throw new IllegalStateException("Unknown Meilisearch task status: " + state);
            }
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for Meilisearch task uid=" + uid, e);
            }
        }
        throw new IllegalStateException("Timed out waiting for Meilisearch task uid=" + uid);
    }

    private static Map<?, ?> requireResponse(Map<?, ?> response) {
        if (response == null) throw new IllegalStateException("Empty Meilisearch response");
        return response;
    }

    private static long requireNonNegativeLong(Object value) {
        if (!(value instanceof Number)) throw new IllegalStateException("Invalid Meilisearch numeric field: " + value);
        try {
            long number = Long.parseLong(value.toString());
            if (number < 0) throw new IllegalStateException("Negative Meilisearch numeric field: " + value);
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid Meilisearch integer field: " + value, e);
        }
    }

    @Override
    public SearchResult search(String query, int page, int size) {
        int offset = (page - 1) * size;
        try {
            var response = restClient
                    .get()
                    .uri(
                            "/indexes/{index}/search?q={q}&limit={limit}&offset={offset}&attributesToHighlight=title,content&attributesToCrop=content:240&highlightPreTag=<em>&highlightPostTag=</em>",
                            index,
                            query,
                            size,
                            offset)
                    .retrieve()
                    .body(Map.class);

            if (response == null) return new SearchResult(List.of(), 0, page, size);

            Object rawHits = response.get("hits");
            if (!(rawHits instanceof List<?> hitsList)) {
                return new SearchResult(List.of(), 0, page, size);
            }
            List<Map<String, Object>> hits = castHitsList(hitsList);

            long total = response.get("estimatedTotalHits") instanceof Number n ? n.longValue() : hits.size();

            return new SearchResult(hits.stream().map(this::fromMap).toList(), total, page, size);
        } catch (Exception e) {
            log.info("MeiliSearch 搜索暂不可用 q={}: {}", query, e.getMessage());
            return new SearchResult(List.of(), 0, page, size);
        }
    }

    /** 安全转换 hits 列表——MeiliSearch 返回的 JSON 结构保证每个元素是 Map。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castHitsList(List<?> hitsList) {
        return (List<Map<String, Object>>) hitsList;
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

    private PostSearchDoc fromMap(Map<String, Object> hit) {
        // MeiliSearch 把 highlight 放在 _formatted 子对象里
        Map<String, Object> formatted = castFormatted(hit);

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
                .tags(castTagsList(hit.get("tags")))
                .seriesName(str(hit.get("seriesName")))
                .build();
    }

    /** 安全转换 _formatted 子对象——MeiliSearch 返回的 JSON 结构保证是 Map。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> castFormatted(Map<String, Object> hit) {
        Object raw = hit.get("_formatted");
        return raw instanceof Map ? (Map<String, Object>) raw : hit;
    }

    /** 安全转换 tags 列表——MeiliSearch 返回的 JSON 结构保证是字符串列表。 */
    @SuppressWarnings("unchecked")
    private static List<String> castTagsList(Object raw) {
        return raw instanceof List ? (List<String>) raw : List.of();
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
