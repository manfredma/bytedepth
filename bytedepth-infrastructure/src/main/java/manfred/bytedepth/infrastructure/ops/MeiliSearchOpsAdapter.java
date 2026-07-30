package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsMeiliSearchPort;
import manfred.bytedepth.app.ops.OpsMeiliSearchStatusDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MeiliSearchOpsAdapter implements OpsMeiliSearchPort {

    private final RestClient restClient;

    public MeiliSearchOpsAdapter(@Value("${bytedepth.search.url}") String url,
                                 @Value("${bytedepth.search.api-key}") String apiKey) {
        this(RestClient.builder()
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build());
    }

    MeiliSearchOpsAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public OpsMeiliSearchStatusDTO inspect() {
        restClient.get().uri("/health").retrieve().toBodilessEntity();
        restClient.get().uri("/stats").retrieve().toBodilessEntity();
        return new OpsMeiliSearchStatusDTO(true, true);
    }
}
