package manfred.bytedepth.infrastructure.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.app.ratelimit.RateLimitDecision;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisRateLimitAdapter implements RateLimitPort {
    private static final String KEY_PREFIX = "bytedepth:rate-limit:";
    private static final Duration EXPIRATION_SAFETY_MARGIN = Duration.ofMinutes(1);
    private final RedisClient redisClient;
    private final Duration requestTimeout;
    private volatile ProxyManager<byte[]> proxyManager;

    public RedisRateLimitAdapter(RateLimitRedisProperties properties) {
        RedisURI uri = RedisURI.create(properties.getHost(), properties.getPort());
        uri.setDatabase(properties.getDatabase());
        uri.setTimeout(properties.getTimeout());
        requestTimeout = properties.getTimeout();
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            uri.setAuthentication(properties.getPassword());
        }
        redisClient = RedisClient.create(uri);
    }

    @Override
    public RateLimitDecision tryConsume(String ruleName, long capacity, Duration period, String identity) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();
        ConsumptionProbe probe = manager().builder().build(redisKey(ruleName, identity), () -> configuration)
                .tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? RateLimitDecision.permit() : RateLimitDecision.rejected(probe.getNanosToWaitForRefill());
    }

    private ProxyManager<byte[]> manager() {
        ProxyManager<byte[]> current = proxyManager;
        if (current != null) return current;
        synchronized (this) {
            if (proxyManager == null) {
                proxyManager = Bucket4jLettuce.casBasedBuilder(redisClient).requestTimeout(requestTimeout)
                        .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(EXPIRATION_SAFETY_MARGIN)).build();
            }
            return proxyManager;
        }
    }

    private byte[] redisKey(String ruleName, String identity) { return (KEY_PREFIX + ruleName + ":" + sha256(identity)).getBytes(StandardCharsets.UTF_8); }
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JVM 缺少 SHA-256", e); }
    }
    @jakarta.annotation.PreDestroy void close() { redisClient.shutdown(); }
}
