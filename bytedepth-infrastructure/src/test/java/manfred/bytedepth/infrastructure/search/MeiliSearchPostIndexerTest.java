package manfred.bytedepth.infrastructure.search;

import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MeiliSearchPostIndexerTest {

    private RestClient restClient;
    private MeiliSearchPostIndexer indexer;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        indexer = new MeiliSearchPostIndexer(restClient);
    }

    private record IndexStubs(RestClient.RequestBodyUriSpec uriSpec,
                              RestClient.ResponseSpec responseSpec) {}

    /**
     * 桩 index() 的调用链：
     * restClient.post() -> uriSpec
     *   .uri(...) -> uriSpec
     *   .contentType(...) -> uriSpec
     *   .body(Object) -> uriSpec (RequestBodySpec 自身，因 RequestBodyUriSpec extends RequestBodySpec)
     *   .retrieve() -> responseSpec
     *   .toBodilessEntity() -> null
     * 关键：RequestBodyUriSpec 同时是 RequestBodySpec，body() 返回 RequestBodySpec(=uriSpec 自身)。
     */
    private IndexStubs stubIndexChain() {
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        doReturn(uriSpec).when(restClient).post();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), any(Object[].class));
        doReturn(uriSpec).when(uriSpec).contentType(any(MediaType.class));
        doReturn(uriSpec).when(uriSpec).body(any(Object.class));
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(null).when(responseSpec).toBodilessEntity();
        return new IndexStubs(uriSpec, responseSpec);
    }

    @Test
    void productionConstructorIsExplicitlyAutowiredWhenTestConstructorExists() throws Exception {
        assertTrue(MeiliSearchPostIndexer.class
                .getDeclaredConstructor(String.class, String.class)
                .isAnnotationPresent(Autowired.class));
    }

    @Test
    void productionConstructorBuildsConfiguredClient() {
        assertDoesNotThrow(() -> new MeiliSearchPostIndexer("http://localhost:7700", "test-key"));
    }

    @Nested
    class IndexTests {

        @Test
        void index_sendsDocumentViaPostAndReturnsBodiless() {
            IndexStubs stubs = stubIndexChain();
            PostSearchDoc doc = PostSearchDoc.builder()
                    .id(1L).slug("hello").title("Title").content("Body")
                    .categoryName("Cat").categorySlug("cat")
                    .tags(List.of("tag1")).seriesName("Series")
                    .build();

            indexer.index(doc);

            verify(restClient).post();
            verify(stubs.responseSpec()).toBodilessEntity();
        }

        @Test
        void index_handlesNullOptionalFields() {
            IndexStubs stubs = stubIndexChain();
            PostSearchDoc doc = PostSearchDoc.builder()
                    .id(2L).title("Title2").content(null)
                    .slug(null).categoryName(null).categorySlug(null)
                    .tags(null).seriesName(null)
                    .build();

            indexer.index(doc);

            verify(stubs.responseSpec()).toBodilessEntity();
        }

        @Test
        @SuppressWarnings("unchecked")
        void index_truncatesLongContent() {
            IndexStubs stubs = stubIndexChain();
            String longContent = "x".repeat(4000);
            PostSearchDoc doc = PostSearchDoc.builder()
                    .id(3L).slug("s").title("T").content(longContent).build();

            indexer.index(doc);

            org.mockito.ArgumentCaptor<List<Map<String, Object>>> captor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(stubs.uriSpec()).body(captor.capture());
            Map<String, Object> sent = captor.getValue().get(0);
            assertEquals(3000, sent.get("content").toString().length());
        }

        @Test
        void index_swallowsException() {
            when(restClient.post()).thenThrow(new RuntimeException("connection refused"));

            PostSearchDoc doc = PostSearchDoc.builder().id(4L).title("T").build();
            indexer.index(doc);

            verify(restClient).post();
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void delete_sendsDeleteRequest() {
            RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
            doReturn(uriSpec).when(restClient).delete();
            doReturn(uriSpec).when(uriSpec).uri(anyString(), any(Object[].class));
            doReturn(responseSpec).when(uriSpec).retrieve();
            doReturn(null).when(responseSpec).toBodilessEntity();

            indexer.delete(99L);

            verify(restClient).delete();
            verify(responseSpec).toBodilessEntity();
        }

        @Test
        void delete_swallowsException() {
            when(restClient.delete()).thenThrow(new RuntimeException("timeout"));

            indexer.delete(99L);

            verify(restClient).delete();
        }
    }

    @Nested
    class SearchTests {

        @SuppressWarnings({"rawtypes", "unchecked"})
        private RestClient.ResponseSpec stubSearch(Map<String, Object> response) {
            RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
            doReturn(uriSpec).when(restClient).get();
            doReturn(uriSpec).when(uriSpec).uri(anyString(), any(Object[].class));
            doReturn(responseSpec).when(uriSpec).retrieve();
            doReturn(response).when(responseSpec).body(Map.class);
            return responseSpec;
        }

        @Test
        void search_returnsParsedHitsWithFormatted() {
            Map<String, Object> hit1 = hitWithFormatted(1, "slug-1");
            Map<String, Object> hit2 = hitWithoutFormatted(2, "slug-2", "not-a-list", null);
            Map<String, Object> response = new HashMap<>();
            response.put("hits", List.of(hit1, hit2));
            response.put("estimatedTotalHits", 2);
            stubSearch(response);

            SearchResult result = indexer.search("query", 1, 10);

            assertEquals(2, result.getHits().size());
            assertEquals(2L, result.getTotalHits());
            assertEquals(1, result.getPage());
            assertEquals(10, result.getSize());

            PostSearchDoc d1 = result.getHits().get(0);
            assertEquals(1L, d1.getId());
            assertEquals("slug-1", d1.getSlug());
            assertEquals("<em>Title</em> 1", d1.getTitle());
            assertEquals("<em>Content</em> 1", d1.getContent());
            assertEquals(List.of("t1", "t2"), d1.getTags());

            PostSearchDoc d2 = result.getHits().get(1);
            assertEquals(2L, d2.getId());
            assertEquals("slug-2", d2.getSlug());
            assertEquals("Title 2", d2.getTitle());
            assertTrue(d2.getTags().isEmpty());
            assertEquals("", d2.getSeriesName());
        }

        @Test
        void search_blankSlugFallsBackToNumericId() {
            Map<String, Object> hit = new HashMap<>();
            hit.put("id", 42);
            hit.put("slug", "");
            hit.put("title", "Title");
            stubSearch(responseWith(hit));

            SearchResult result = indexer.search("q", 1, 5);
            assertEquals("42", result.getHits().get(0).getSlug());
        }

        @Test
        void search_missingSlugFallsBackToNumericId() {
            Map<String, Object> hit = new HashMap<>();
            hit.put("id", 7);
            hit.put("title", "Title");
            stubSearch(responseWith(hit));

            SearchResult result = indexer.search("q", 1, 5);
            assertEquals("7", result.getHits().get(0).getSlug());
        }

        @Test
        void search_nonNumberEstimatedTotalHitsUsesHitsSize() {
            Map<String, Object> hit = new HashMap<>();
            hit.put("id", 1);
            hit.put("slug", "s");
            hit.put("title", "T");
            Map<String, Object> response = new HashMap<>();
            response.put("hits", List.of(hit));
            response.put("estimatedTotalHits", "not-a-number");
            stubSearch(response);

            SearchResult result = indexer.search("q", 1, 5);
            assertEquals(1L, result.getTotalHits());
        }

        @Test
        void search_nullResponseReturnsEmpty() {
            stubSearch(null);

            SearchResult result = indexer.search("q", 1, 5);
            assertTrue(result.getHits().isEmpty());
            assertEquals(0L, result.getTotalHits());
        }

        @Test
        void search_nullHitsReturnsEmpty() {
            Map<String, Object> response = new HashMap<>();
            response.put("hits", null);
            response.put("estimatedTotalHits", 0);
            stubSearch(response);

            SearchResult result = indexer.search("q", 1, 5);
            assertTrue(result.getHits().isEmpty());
            assertEquals(0L, result.getTotalHits());
        }

        @Test
        void search_exceptionReturnsEmpty() {
            when(restClient.get()).thenThrow(new RuntimeException("network error"));

            SearchResult result = indexer.search("q", 1, 5);
            assertTrue(result.getHits().isEmpty());
            assertEquals(0L, result.getTotalHits());
        }

        @Test
        void search_computesOffsetForPage2() {
            Map<String, Object> response = new HashMap<>();
            response.put("hits", List.of());
            response.put("estimatedTotalHits", 0);
            stubSearch(response);

            SearchResult result = indexer.search("q", 2, 10);
            assertTrue(result.getHits().isEmpty());
            assertEquals(2, result.getPage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void toMap_nullOptionalFieldsBecomeEmptyStrings() {
        IndexStubs stubs = stubIndexChain();
        PostSearchDoc doc = PostSearchDoc.builder()
                .id(10L).title("T").content(null).slug(null)
                .categoryName(null).categorySlug(null).tags(null).seriesName(null)
                .build();
        indexer.index(doc);

        org.mockito.ArgumentCaptor<List<Map<String, Object>>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stubs.uriSpec()).body(captor.capture());
        Map<String, Object> m = captor.getValue().get(0);
        assertEquals("", m.get("slug"));
        assertEquals("", m.get("categoryName"));
        assertEquals("", m.get("categorySlug"));
        assertEquals(List.of(), m.get("tags"));
        assertEquals("", m.get("seriesName"));
        assertEquals("", m.get("content"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toMap_truncateAtBoundary() {
        IndexStubs stubs = stubIndexChain();
        String content = "x".repeat(3000);
        PostSearchDoc doc = PostSearchDoc.builder()
                .id(11L).title("T").content(content).build();
        indexer.index(doc);

        org.mockito.ArgumentCaptor<List<Map<String, Object>>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stubs.uriSpec()).body(captor.capture());
        Map<String, Object> m = captor.getValue().get(0);
        assertEquals(3000, m.get("content").toString().length());
    }

    @Test
    void fromMap_usesHitDirectlyWhenNoFormatted() {
        Map<String, Object> hit = new HashMap<>();
        hit.put("id", 5);
        hit.put("slug", "my-slug");
        hit.put("title", "My Title");
        hit.put("content", "My Content");
        hit.put("categoryName", "Cat");
        hit.put("categorySlug", "cat");
        hit.put("tags", List.of("a"));
        hit.put("seriesName", "Ser");

        stubSearch(responseWith(hit));

        SearchResult result = indexer.search("q", 1, 5);
        PostSearchDoc d = result.getHits().get(0);
        assertEquals("My Title", d.getTitle());
        assertEquals("My Content", d.getContent());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RestClient.ResponseSpec stubSearch(Map<String, Object> response) {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        doReturn(uriSpec).when(restClient).get();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), any(Object[].class));
        doReturn(responseSpec).when(uriSpec).retrieve();
        doReturn(response).when(responseSpec).body(Map.class);
        return responseSpec;
    }

    private Map<String, Object> hitWithFormatted(int id, String slug) {
        Map<String, Object> hit = new HashMap<>();
        hit.put("id", id);
        hit.put("slug", slug);
        hit.put("title", "Title " + id);
        hit.put("content", "Content " + id);
        hit.put("categoryName", "Cat");
        hit.put("categorySlug", "cat");
        hit.put("tags", List.of("t1", "t2"));
        hit.put("seriesName", "Series");
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("title", "<em>Title</em> " + id);
        formatted.put("content", "<em>Content</em> " + id);
        hit.put("_formatted", formatted);
        return hit;
    }

    private Map<String, Object> hitWithoutFormatted(int id, String slug, Object tags, Object series) {
        Map<String, Object> hit = new HashMap<>();
        hit.put("id", id);
        hit.put("slug", slug);
        hit.put("title", "Title " + id);
        hit.put("content", "Content " + id);
        hit.put("categoryName", "Cat");
        hit.put("categorySlug", "cat");
        hit.put("tags", tags);
        hit.put("seriesName", series);
        return hit;
    }

    private Map<String, Object> responseWith(Map<String, Object> hit) {
        Map<String, Object> response = new HashMap<>();
        response.put("hits", List.of(hit));
        response.put("estimatedTotalHits", 1);
        return response;
    }
}
