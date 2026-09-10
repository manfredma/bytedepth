package manfred.bytedepth.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisRateLimitAdapterIT {
    private static final String KEY_PREFIX = "bytedepth:rate-limit:";

    @Test
    void consumesAndRejectsDistributedBuckets() {
        RateLimitRedisProperties properties = stagingProperties();
        String rule = "test-" + UUID.randomUUID();
        RedisRateLimitAdapter adapter = new RedisRateLimitAdapter(properties);
        try {
            assertThat(adapter.tryConsume(rule, 1, Duration.ofMinutes(1), "visitor").allowed()).isTrue();
            assertThat(adapter.tryConsume(rule, 1, Duration.ofMinutes(1), "visitor").allowed()).isFalse();
        } finally {
            try {
                deleteRateLimitKey(properties, rule, "visitor");
            } finally {
                adapter.close();
            }
        }
    }

    static RateLimitRedisProperties stagingProperties(String host, String password) {
        RateLimitRedisProperties properties = new RateLimitRedisProperties();
        properties.setHost(requireNonBlank(host, "bytedepth.it.redis.host"));
        properties.setPort(6379);
        properties.setPassword(requireNonBlank(password, "bytedepth.it.redis.password"));
        return properties;
    }

    private static RateLimitRedisProperties stagingProperties() {
        RateLimitRedisProperties properties = stagingProperties(
                System.getProperty("bytedepth.it.redis.host"),
                System.getProperty("bytedepth.it.redis.password"));
        String port = requireNonBlank(System.getProperty("bytedepth.it.redis.port"), "bytedepth.it.redis.port");
        try {
            properties.setPort(Integer.parseInt(port));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("System property bytedepth.it.redis.port must be a number", exception);
        }
        return properties;
    }

    private static void deleteRateLimitKey(RateLimitRedisProperties properties, String rule, String identity) {
        RedisURI uri = RedisURI.create(properties.getHost(), properties.getPort());
        uri.setDatabase(properties.getDatabase());
        uri.setTimeout(properties.getTimeout());
        uri.setAuthentication(properties.getPassword());
        RedisClient cleanupClient = RedisClient.create(uri);
        try (StatefulRedisConnection<String, String> connection = cleanupClient.connect()) {
            connection.sync().del(redisKey(rule, identity));
        } finally {
            cleanupClient.shutdown();
        }
    }

    private static String redisKey(String rule, String identity) {
        return KEY_PREFIX + rule + ":" + sha256(identity);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256", exception);
        }
    }

    private static String requireNonBlank(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required staging Redis system property is missing: " + propertyName);
        }
        return value;
    }
}
