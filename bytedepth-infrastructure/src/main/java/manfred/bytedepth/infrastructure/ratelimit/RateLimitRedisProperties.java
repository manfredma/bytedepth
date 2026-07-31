package manfred.bytedepth.infrastructure.ratelimit;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bytedepth.rate-limit.redis")
public class RateLimitRedisProperties {
    private String host = "localhost";
    private int port = 6379;
    private String password = "";
    private int database;
    private Duration timeout = Duration.ofSeconds(1);
}
