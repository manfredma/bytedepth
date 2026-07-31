package manfred.bytedepth.infrastructure.stats;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.app.analytics.ReadingProgressTokenPort;
import manfred.bytedepth.domain.stats.PostViewedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis-backed implementation of the short-lived, post-bound reading-progress token. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisReadingProgressTokenAdapter implements ReadingProgressTokenPort {

    private static final String KEY_PREFIX = "bytedepth:reading-progress:";
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    @Override
    @EventListener
    public void issue(PostViewedEvent event) {
        try {
            redisTemplate.opsForValue().set(key(event.visitToken()), event.postId().toString(), TOKEN_TTL);
        } catch (RuntimeException e) {
            log.warn("阅读进度令牌签发失败 postId={}", event.postId(), e);
        }
    }

    @Override
    public boolean belongsToPost(String token, Long postId) {
        try {
            return postId.toString().equals(redisTemplate.opsForValue().get(key(token)));
        } catch (RuntimeException e) {
            log.warn("阅读进度令牌校验失败 postId={}", postId, e);
            return false;
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
