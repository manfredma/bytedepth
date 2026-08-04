package manfred.bytedepth.infrastructure.search;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MinTest {
    @Test
    void delete_passesTheIndexAndPostIdAsUriVariables() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        doReturn(uriSpec).when(restClient).delete();
        doReturn(uriSpec).when(uriSpec).uri(anyString(), any(Object[].class));
        doReturn(responseSpec).when(uriSpec).retrieve();

        new MeiliSearchPostIndexer(restClient).delete(99L);

        verify(uriSpec).uri("/indexes/{index}/documents/{id}", "posts", 99L);
        verify(responseSpec).toBodilessEntity();
    }
}
