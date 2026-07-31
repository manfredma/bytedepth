package manfred.bytedepth.infrastructure.stats;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import manfred.bytedepth.domain.stats.PostViewedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisReadingProgressTokenAdapterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private RedisReadingProgressTokenAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        adapter = new RedisReadingProgressTokenAdapter(redisTemplate);
    }

    @Test
    void issuesPostBoundTokenForOneDay() {
        adapter.issue(new PostViewedEvent(12L, null, "203.0.113.1", "agent", null,
                "token-1", LocalDateTime.now()));

        verify(values).set("bytedepth:reading-progress:token-1", "12", Duration.ofHours(24));
    }

    @Test
    void acceptsOnlyTokenBoundToSamePost() {
        when(values.get("bytedepth:reading-progress:token-1")).thenReturn("12");

        assertTrue(adapter.belongsToPost("token-1", 12L));
        assertFalse(adapter.belongsToPost("token-1", 13L));
    }

    @Test
    void toleratesRedisFailuresWithoutIssuingOrAcceptingAToken() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(values).set("bytedepth:reading-progress:token-1", "12", Duration.ofHours(24));
        adapter.issue(new PostViewedEvent(12L, null, "203.0.113.1", "agent", null,
                "token-1", LocalDateTime.now()));
        when(values.get("bytedepth:reading-progress:token-1"))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertFalse(adapter.belongsToPost("token-1", 12L));
    }
}
