package manfred.bytedepth.infrastructure.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MeiliSearchPostIndexerTest {
    private RestClient restClient;
    private MeiliSearchPostIndexer indexer;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        indexer = new MeiliSearchPostIndexer(restClient);
    }

    private void useServer() {
        var builder = RestClient.builder().baseUrl("http://meili");
        server = MockRestServiceServer.bindTo(builder).build();
        indexer = new MeiliSearchPostIndexer(builder.build(), "isolated_posts");
    }

    private void accepted(HttpMethod method, String path, String response) {
        server.expect(requestTo("http://meili/indexes/isolated_posts/documents" + path))
                .andExpect(method(method))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void task(String response) {
        server.expect(requestTo("http://meili/tasks/7")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void page(int offset, String response) {
        server.expect(requestTo("http://meili/indexes/isolated_posts/documents?fields=id&limit=1000&offset=" + offset))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private PostSearchDoc doc() {
        return PostSearchDoc.builder().id(1L).title("Title").build();
    }

    @Test
    void bulkIndexSubmitsAllDocumentsInOneTask() {
        useServer();
        server.expect(requestTo("http://meili/indexes/isolated_posts/documents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"id\":1,\"title\":\"Title\"},{\"id\":2,\"title\":\"Second\"}]"))
                .andRespond(withSuccess("{\"taskUid\":7}", MediaType.APPLICATION_JSON));
        task("{\"status\":\"succeeded\"}");
        assertDoesNotThrow(() -> indexer.indexAll(
                List.of(doc(), PostSearchDoc.builder().id(2L).title("Second").build())));
        server.verify();
    }

    @Test
    void bulkDeletionSubmitsIdsInOneScopedTask() {
        useServer();
        server.expect(requestTo("http://meili/indexes/isolated_posts/documents/delete-batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[1,2]"))
                .andRespond(withSuccess("{\"taskUid\":7}", MediaType.APPLICATION_JSON));
        task("{\"status\":\"succeeded\"}");
        assertDoesNotThrow(() -> indexer.deleteAll(List.of(1L, 2L)));
        server.verify();
    }

    @Test
    void emptyBatchesDoNotSubmitTasks() {
        useServer();
        indexer.indexAll(List.of());
        indexer.deleteAll(List.of());
        server.verify();
    }

    @Test
    void productionConstructorBuildsConfiguredClientAndIsAutowired() throws Exception {
        assertTrue(MeiliSearchPostIndexer.class
                .getDeclaredConstructor(String.class, String.class, String.class)
                .isAnnotationPresent(Autowired.class));
        assertDoesNotThrow(() -> new MeiliSearchPostIndexer("http://localhost:7700", "key", "posts"));
    }

    @Test
    void indexWaitsThroughEnqueuedAndProcessingUntilSucceeded() {
        useServer();
        accepted(HttpMethod.POST, "", "{\"taskUid\":7}");
        task("{\"status\":\"enqueued\"}");
        task("{\"status\":\"processing\"}");
        task("{\"status\":\"succeeded\"}");
        indexer.index(doc());
        server.verify();
    }

    @Test
    void deleteWaitsForSuccessInConfiguredIndex() {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        task("{\"status\":\"succeeded\"}");
        indexer.delete(1L);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "canceled", "unknown", ""})
    void unsuccessfulTaskIsReported(String status) {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        task("{\"status\":\"" + status + "\",\"error\":{\"message\":\"bad document\"}}");
        assertThrows(IllegalStateException.class, () -> indexer.delete(1L));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "{}",
                "{\"taskUid\":-1}",
                "{\"taskUid\":1.5}",
                "{\"taskUid\":\"7\"}",
                "{\"taskUid\":9223372036854775808}"
            })
    void malformedAcceptanceFails(String response) {
        useServer();
        accepted(HttpMethod.POST, "", response);
        assertThrows(IllegalStateException.class, () -> indexer.index(doc()));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"status\":null}", "{\"status\":3}"})
    void malformedTaskFails(String response) {
        useServer();
        accepted(HttpMethod.POST, "", "{\"taskUid\":7}");
        task(response);
        assertThrows(IllegalStateException.class, () -> indexer.index(doc()));
        server.verify();
    }

    @Test
    void pendingTaskTimesOut() {
        var builder = RestClient.builder().baseUrl("http://meili");
        server = MockRestServiceServer.bindTo(builder).build();
        indexer = new MeiliSearchPostIndexer(builder.build(), "isolated_posts", Duration.ZERO, Duration.ofMillis(1));
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        assertThrows(IllegalStateException.class, () -> indexer.delete(1L));
        server.verify();
    }

    @Test
    void interruptionDuringPendingTaskAbortsAndPreservesFlag() {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        server.expect(requestTo("http://meili/tasks/7"))
                .andExpect(request -> Thread.currentThread().interrupt())
                .andRespond(withSuccess("{\"status\":\"processing\"}", MediaType.APPLICATION_JSON));
        try {
            assertThrows(IllegalStateException.class, () -> indexer.delete(1L));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        server.verify();
    }

    @Test
    void preInterruptedWritesAbortBeforeSubmittingRequests() {
        useServer();
        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> indexer.index(doc()));
            assertThrows(IllegalStateException.class, () -> indexer.delete(1L));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        server.verify();
    }

    @Test
    void deletingAbsentIndexSucceedsOnSynchronousExactMissingIndexError() {
        useServer();
        server.expect(requestTo("http://meili/indexes/isolated_posts/documents/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"index_not_found\"}"));
        assertDoesNotThrow(() -> indexer.delete(1L));
        server.verify();
    }

    @Test
    void deletingAbsentIndexSucceedsOnAsynchronousExactMissingIndexError() {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        task("{\"status\":\"failed\",\"error\":{\"code\":\"index_not_found\"}}");
        assertDoesNotThrow(() -> indexer.delete(1L));
        server.verify();
    }

    @Test
    void indexingStillFailsForAsynchronousMissingIndexError() {
        useServer();
        accepted(HttpMethod.POST, "", "{\"taskUid\":7}");
        task("{\"status\":\"failed\",\"error\":{\"code\":\"index_not_found\"}}");
        assertThrows(IllegalStateException.class, () -> indexer.index(doc()));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":\"other\"}", "{}", "invalid json", ""})
    void deleteDoesNotAcceptOtherSynchronousNotFoundErrors(String response) {
        useServer();
        server.expect(requestTo("http://meili/indexes/isolated_posts/documents/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response));
        assertThrows(org.springframework.web.client.RestClientException.class, () -> indexer.delete(1L));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"code\":\"other\"}", "\"index_not_found\""})
    void deleteDoesNotAcceptOtherAsynchronousFailures(String error) {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        task("{\"status\":\"failed\",\"error\":" + error + "}");
        assertThrows(IllegalStateException.class, () -> indexer.delete(1L));
        server.verify();
    }

    @Test
    void deleteDoesNotAcceptMissingIndexErrorFromTaskEndpoint() {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        server.expect(requestTo("http://meili/tasks/7"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"index_not_found\"}"));
        assertThrows(org.springframework.web.client.RestClientException.class, () -> indexer.delete(1L));
        server.verify();
    }

    @Test
    void writeTransportFailuresPropagate() {
        useServer();
        server.expect(requestTo("http://meili/indexes/isolated_posts/documents"))
                .andRespond(withException(new IOException("offline")));
        assertThrows(org.springframework.web.client.RestClientException.class, () -> indexer.index(doc()));
        server.verify();
    }

    @Test
    void deleteTransportFailuresPropagate() {
        useServer();
        server.expect(requestTo("http://meili/indexes/isolated_posts/documents/1"))
                .andRespond(withServerError());
        assertThrows(org.springframework.web.client.RestClientException.class, () -> indexer.delete(1L));
        server.verify();
    }

    @Test
    void taskTransportFailuresPropagateAfterAcceptedWrite() {
        useServer();
        accepted(HttpMethod.POST, "", "{\"taskUid\":7}");
        server.expect(requestTo("http://meili/tasks/7")).andRespond(withServerError());
        assertThrows(org.springframework.web.client.RestClientException.class, () -> indexer.index(doc()));
        server.verify();
    }

    @Test
    void acceptedDeletionRechecksSameTaskAfterOneReadTimeoutWithoutResubmitting() {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        server.expect(requestTo("http://meili/tasks/7"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        task("{\"status\":\"succeeded\"}");
        assertDoesNotThrow(() -> indexer.delete(1L));
        server.verify();
    }

    @Test
    void readTimeoutBudgetIsNotResetByPendingTaskResponse() {
        useServer();
        accepted(HttpMethod.POST, "", "{\"taskUid\":7}");
        server.expect(requestTo("http://meili/tasks/7"))
                .andRespond(withException(new SocketTimeoutException("first timeout")));
        task("{\"status\":\"processing\"}");
        server.expect(requestTo("http://meili/tasks/7"))
                .andRespond(withException(new SocketTimeoutException("second timeout")));
        var failure =
                assertThrows(org.springframework.web.client.ResourceAccessException.class, () -> indexer.index(doc()));
        assertEquals("second timeout", failure.getCause().getMessage());
        server.verify();
    }

    @Test
    void taskReadDoesNotRetryOtherNetworkFailures() {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        server.expect(requestTo("http://meili/tasks/7")).andRespond(withException(new IOException("offline")));
        assertThrows(org.springframework.web.client.ResourceAccessException.class, () -> indexer.delete(1L));
        server.verify();
    }

    @Test
    void taskReadTimeoutDoesNotExtendTaskDeadline() {
        var builder = RestClient.builder().baseUrl("http://meili");
        server = MockRestServiceServer.bindTo(builder).build();
        indexer = new MeiliSearchPostIndexer(builder.build(), "isolated_posts", Duration.ofMillis(100), Duration.ZERO);
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        server.expect(requestTo("http://meili/tasks/7")).andRespond(request -> {
            try {
                Thread.sleep(Duration.ofMillis(200));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
            throw new SocketTimeoutException("Read timed out");
        });
        var failure = assertThrows(IllegalStateException.class, () -> indexer.delete(1L));
        assertTrue(failure.getMessage().contains("Timed out waiting"));
        server.verify();
    }

    @Test
    void taskReadTimeoutPreservesInterruptionAndDoesNotRetry() {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        server.expect(requestTo("http://meili/tasks/7")).andRespond(request -> {
            Thread.currentThread().interrupt();
            throw new SocketTimeoutException("Read timed out");
        });
        try {
            assertThrows(IllegalStateException.class, () -> indexer.delete(1L));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        server.verify();
    }

    @Test
    void writeReadTimeoutIsNeverResubmitted() {
        useServer();
        server.expect(requestTo("http://meili/indexes/isolated_posts/documents"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        assertThrows(org.springframework.web.client.ResourceAccessException.class, () -> indexer.index(doc()));
        server.verify();
    }

    @Test
    void taskFailureAfterReadTimeoutStillFails() {
        useServer();
        accepted(HttpMethod.DELETE, "/1", "{\"taskUid\":7}");
        server.expect(requestTo("http://meili/tasks/7"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        task("{\"status\":\"failed\",\"error\":{\"code\":\"internal\"}}");
        assertThrows(IllegalStateException.class, () -> indexer.delete(1L));
        server.verify();
    }

    @Test
    void searchUsesConfiguredIndex() {
        useServer();
        server.expect(request -> assertEquals(
                        "/indexes/isolated_posts/search", request.getURI().getPath()))
                .andRespond(withSuccess("{\"hits\":[]}", MediaType.APPLICATION_JSON));
        assertTrue(indexer.search("query", 1, 10).getHits().isEmpty());
        server.verify();
    }

    @Test
    void collectsAllPagesBeforeReturningIds() {
        useServer();
        page(0, "{\"results\":[{\"id\":1},{\"id\":2}],\"total\":3,\"offset\":0,\"limit\":1000}");
        page(2, "{\"results\":[{\"id\":3}],\"total\":3,\"offset\":2,\"limit\":1000}");
        assertEquals(List.of(1L, 2L, 3L), indexer.findIndexedIds());
        server.verify();
    }

    @Test
    void emptyIndexReturnsEmptyIds() {
        useServer();
        page(0, "{\"results\":[],\"total\":0,\"offset\":0,\"limit\":1000}");
        assertEquals(List.of(), indexer.findIndexedIds());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "{}",
                "{\"results\":{},\"total\":0}",
                "{\"results\":[{}],\"total\":1}",
                "{\"results\":[{\"id\":\"bad\"}],\"total\":1}",
                "{\"results\":[],\"total\":1}",
                "{\"results\":[],\"total\":-1}",
                "{\"results\":[{\"id\":1.5}],\"total\":1}",
                "{\"results\":[{\"id\":1}],\"total\":0}"
            })
    void malformedDocumentPagesFail(String response) {
        useServer();
        page(
                0,
                response.startsWith("{")
                        ? response.substring(0, response.length() - 1) + (response.length() > 2 ? "," : "")
                                + "\"offset\":0,\"limit\":1000}"
                        : response);
        assertThrows(IllegalStateException.class, () -> indexer.findIndexedIds());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"results\":[],\"total\":0,\"offset\":1,\"limit\":1000}",
                "{\"results\":[],\"total\":0,\"offset\":0,\"limit\":10}",
                "{\"results\":[3],\"total\":1,\"offset\":0,\"limit\":1000}"
            })
    void invalidPaginationAndDocumentShapesFail(String response) {
        useServer();
        page(0, response);
        assertThrows(IllegalStateException.class, () -> indexer.findIndexedIds());
        server.verify();
    }

    @Test
    void missingIndexReturnsEmptyOnlyForExactErrorCode() {
        useServer();
        server.expect(anything())
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"index_not_found\"}"));
        assertEquals(List.of(), indexer.findIndexedIds());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":\"other\"}", "{}", "invalid json", ""})
    void otherNotFoundResponsesFail(String response) {
        useServer();
        server.expect(anything())
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response));
        assertThrows(org.springframework.web.client.RestClientException.class, () -> indexer.findIndexedIds());
        server.verify();
    }

    @Test
    void listTransportFailurePropagates() {
        useServer();
        server.expect(anything()).andRespond(withException(new IOException("offline")));
        assertThrows(org.springframework.web.client.RestClientException.class, () -> indexer.findIndexedIds());
        server.verify();
    }

    @Test
    void optionalFieldsAndContentAreSerializedCorrectly() {
        useServer();
        server.expect(anything())
                .andExpect(
                        content()
                                .json(
                                        "[{\"id\":1,\"title\":\"Title\",\"content\":\"\",\"slug\":\"\",\"categoryName\":\"\",\"categorySlug\":\"\",\"tags\":[],\"seriesName\":\"\"}]"))
                .andRespond(withSuccess("{\"taskUid\":7}", MediaType.APPLICATION_JSON));
        task("{\"status\":\"succeeded\"}");
        indexer.index(doc());
        server.verify();
    }

    @Test
    void populatedFieldsAndLongContentAreSerializedCorrectly() {
        useServer();
        server.expect(anything())
                .andExpect(
                        content()
                                .json(
                                        "[{\"id\":1,\"title\":\"Title\",\"content\":\"" + "x".repeat(3000)
                                                + "\",\"slug\":\"slug\",\"categoryName\":\"cat\",\"categorySlug\":\"cat\",\"tags\":[\"t\"],\"seriesName\":\"series\"}]"))
                .andRespond(withSuccess("{\"taskUid\":7}", MediaType.APPLICATION_JSON));
        task("{\"status\":\"succeeded\"}");
        indexer.index(PostSearchDoc.builder()
                .id(1L)
                .title("Title")
                .content("x".repeat(3001))
                .slug("slug")
                .categoryName("cat")
                .categorySlug("cat")
                .tags(List.of("t"))
                .seriesName("series")
                .build());
        server.verify();
    }

    @Test
    void shortContentIsPreserved() {
        useServer();
        server.expect(anything())
                .andExpect(content().json("[{\"content\":\"body\"}]"))
                .andRespond(withSuccess("{\"taskUid\":7}", MediaType.APPLICATION_JSON));
        task("{\"status\":\"succeeded\"}");
        indexer.index(
                PostSearchDoc.builder().id(1L).title("Title").content("body").build());
        server.verify();
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
