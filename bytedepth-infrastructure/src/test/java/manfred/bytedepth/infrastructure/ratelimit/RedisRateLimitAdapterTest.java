package manfred.bytedepth.infrastructure.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisRateLimitAdapterTest {

    @Test
    void consumesAndRejectsDistributedBuckets() {
        RedisRateLimitAdapter adapter = new RedisRateLimitAdapter(new RateLimitRedisProperties());
        try {
            String rule = "test-" + UUID.randomUUID();
            assertTrue(adapter.tryConsume(rule, 1, Duration.ofMinutes(1), "visitor").allowed());
            assertFalse(adapter.tryConsume(rule, 1, Duration.ofMinutes(1), "visitor").allowed());
        } finally {
            adapter.close();
        }
    }

    @Test
    void acceptsBlankAndConfiguredRedisPasswords() {
        RateLimitRedisProperties blank = new RateLimitRedisProperties();
        blank.setPassword(null);
        RedisRateLimitAdapter blankAdapter = new RedisRateLimitAdapter(blank);
        blankAdapter.close();

        RateLimitRedisProperties configured = new RateLimitRedisProperties();
        configured.setPassword("secret");
        RedisRateLimitAdapter configuredAdapter = new RedisRateLimitAdapter(configured);
        configuredAdapter.close();
    }
}
