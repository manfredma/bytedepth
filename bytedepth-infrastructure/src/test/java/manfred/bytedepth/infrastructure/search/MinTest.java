package manfred.bytedepth.infrastructure.search;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MinTest {
    @Test
    void deletePassesTheDefaultIndexAndPostIdAndWaitsForCompletion() {
        var builder = RestClient.builder().baseUrl("http://meili");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://meili/indexes/posts/documents/99"))
                .andRespond(withSuccess("{\"taskUid\":7}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://meili/tasks/7"))
                .andRespond(withSuccess("{\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
        new MeiliSearchPostIndexer(builder.build()).delete(99L);
        server.verify();
    }
}
