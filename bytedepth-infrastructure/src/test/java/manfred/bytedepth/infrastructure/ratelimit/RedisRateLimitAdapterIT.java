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
                requireEnv("BYTEDEPTH_STAGING_IT_REDIS_HOST"),
                requireEnv("BYTEDEPTH_STAGING_IT_REDIS_PASSWORD"));
        String port = requireEnv("BYTEDEPTH_STAGING_IT_REDIS_PORT");
        try {
            properties.setPort(Integer.parseInt(port));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Environment variable BYTEDEPTH_STAGING_IT_REDIS_PORT must be a number", exception);
        }
        String database = requireEnv("BYTEDEPTH_STAGING_IT_REDIS_DATABASE");
        try {
            properties.setDatabase(Integer.parseInt(database));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Environment variable BYTEDEPTH_STAGING_IT_REDIS_DATABASE must be a number", exception);
        }
        properties.setKeyNamespace(requireEnv("BYTEDEPTH_STAGING_IT_REDIS_KEY_NAMESPACE"));
        return properties;
    }

    private static String requireEnv(String name) {
        return requireNonBlank(System.getenv(name), name);
    }

    private static void deleteRateLimitKey(RateLimitRedisProperties properties, String rule, String identity) {
        RedisURI uri = RedisURI.create(properties.getHost(), properties.getPort());
        uri.setDatabase(properties.getDatabase());
        uri.setTimeout(properties.getTimeout());
        uri.setAuthentication(properties.getPassword());
        RedisClient cleanupClient = RedisClient.create(uri);
        try (StatefulRedisConnection<String, String> connection = cleanupClient.connect()) {
            connection.sync().del(redisKey(properties, rule, identity));
        } finally {
            cleanupClient.shutdown();
        }
    }

    private static String redisKey(RateLimitRedisProperties properties, String rule, String identity) {
        return properties.getKeyNamespace() + KEY_PREFIX + rule + ":" + sha256(identity);
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
