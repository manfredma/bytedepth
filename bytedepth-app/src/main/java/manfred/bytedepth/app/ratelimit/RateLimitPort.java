package manfred.bytedepth.app.ratelimit;

import java.time.Duration;

/** Outbound port for consuming a distributed rate-limit bucket. */
public interface RateLimitPort {
    RateLimitDecision tryConsume(String ruleName, long capacity, Duration period, String identity);
}
