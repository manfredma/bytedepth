package manfred.bytedepth.adapter.web.ratelimit;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bytedepth.rate-limit")
public class RateLimitProperties {

    private Redis redis = new Redis();
    private Rule loginIp = new Rule(5, Duration.ofMinutes(1));
    private Rule loginUsername = new Rule(10, Duration.ofMinutes(15));
    private Rule registerIp = new Rule(3, Duration.ofHours(1));
    private Rule commentRatingIp = new Rule(10, Duration.ofMinutes(1));
    private Rule uploadIp = new Rule(20, Duration.ofHours(1));

    @PostConstruct
    void validate() {
        if (redis == null || redis.host == null || redis.host.isBlank() || redis.port <= 0 || redis.port > 65535
                || redis.database < 0 || redis.timeout == null || redis.timeout.isZero() || redis.timeout.isNegative()) {
            throw new IllegalStateException("限流 Redis 配置无效");
        }
        validate("login-ip", loginIp);
        validate("login-username", loginUsername);
        validate("register-ip", registerIp);
        validate("comment-rating-ip", commentRatingIp);
        validate("upload-ip", uploadIp);
    }

    private void validate(String name, Rule rule) {
        if (rule == null || rule.capacity <= 0 || rule.period == null || rule.period.isZero() || rule.period.isNegative()) {
            throw new IllegalStateException("限流规则配置无效: " + name);
        }
    }

    @Getter
    @Setter
    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private int database;
        private Duration timeout = Duration.ofSeconds(1);
    }

    @Getter
    @Setter
    public static class Rule {
        private long capacity;
        private Duration period;

        public Rule() {
        }

        Rule(long capacity, Duration period) {
            this.capacity = capacity;
            this.period = period;
        }
    }
}
