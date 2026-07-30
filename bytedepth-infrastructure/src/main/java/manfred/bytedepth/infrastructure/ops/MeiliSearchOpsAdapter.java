package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsMeiliSearchPort;
import manfred.bytedepth.app.ops.OpsMeiliSearchStatusDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class MeiliSearchOpsAdapter implements OpsMeiliSearchPort {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(1);

    private final RestClient restClient;

    @Autowired
    public MeiliSearchOpsAdapter(@Value("${bytedepth.search.url}") String url,
                                 @Value("${bytedepth.search.api-key}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory())
                .build());
    }

    MeiliSearchOpsAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

    @Override
    public OpsMeiliSearchStatusDTO inspect() {
        restClient.get().uri("/health").retrieve().toBodilessEntity();
        restClient.get().uri("/stats").retrieve().toBodilessEntity();
        return new OpsMeiliSearchStatusDTO(true, true);
    }
}
