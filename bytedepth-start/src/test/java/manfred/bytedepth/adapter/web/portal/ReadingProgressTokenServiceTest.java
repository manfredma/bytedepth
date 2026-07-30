package manfred.bytedepth.adapter.web.portal;

import static org.assertj.core.api.Assertions.assertThat;
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

class ReadingProgressTokenServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private ReadingProgressTokenService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        service = new ReadingProgressTokenService(redisTemplate);
    }

    @Test
    void issuesPostBoundTokenForOneDay() {
        service.issue(new PostViewedEvent(12L, null, "203.0.113.1", "agent", null,
                "token-1", LocalDateTime.now()));

        verify(values).set("bytedepth:reading-progress:token-1", "12", Duration.ofHours(24));
    }

    @Test
    void acceptsOnlyTokenBoundToSamePost() {
        when(values.get("bytedepth:reading-progress:token-1")).thenReturn("12");

        assertThat(service.belongsToPost("token-1", 12L)).isTrue();
        assertThat(service.belongsToPost("token-1", 13L)).isFalse();
    }

    @Test
    void toleratesRedisFailuresWithoutIssuingOrAcceptingAToken() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(values).set("bytedepth:reading-progress:token-1", "12", Duration.ofHours(24));
        service.issue(new PostViewedEvent(12L, null, "203.0.113.1", "agent", null,
                "token-1", LocalDateTime.now()));
        when(values.get("bytedepth:reading-progress:token-1"))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThat(service.belongsToPost("token-1", 12L)).isFalse();
    }
}
