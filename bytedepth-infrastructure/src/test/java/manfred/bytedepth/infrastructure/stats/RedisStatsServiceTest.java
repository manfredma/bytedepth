package manfred.bytedepth.infrastructure.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import manfred.bytedepth.infrastructure.redis.RedisKeyNamespace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

class RedisStatsServiceTest {

    @Test
    void flushToDB_scansPostViewKeysAndSkipsKeysThatExpireDuringFlush() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Cursor<String> scanCursor = cursor(List.of("pv:post:12", "pv:post:13"));
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(scanCursor);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("pv:post:12")).thenReturn("42");
        when(values.get("pv:post:13")).thenReturn(null);

        new RedisStatsService(redisTemplate, jdbcTemplate, new RedisKeyNamespace("")).flushToDB();

        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redisTemplate).scan(options.capture());
        assertEquals("pv:post:*", options.getValue().getPattern());
        verify(jdbcTemplate).update(
                eq("INSERT INTO page_stats (path, pv_count, updated_at) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE pv_count = ?, updated_at = ?"),
                eq("/posts/12"), eq(42L), any(LocalDateTime.class), eq(42L), any(LocalDateTime.class));
        verify(jdbcTemplate, never()).update(
                any(String.class), eq("/posts/13"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("increment() calls redis increment on pv:post:{postId}")
    void increment_callsRedisIncrement() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(values);

        new RedisStatsService(redisTemplate, jdbcTemplate, new RedisKeyNamespace("")).increment(100L);

        verify(values).increment("pv:post:100");
    }

    @Test
    @DisplayName("getCount() returns 0 when redis returns null")
    void getCount_returnsZeroWhenNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("pv:post:200")).thenReturn(null);

        long count = new RedisStatsService(redisTemplate, jdbcTemplate, new RedisKeyNamespace("")).getCount(200L);

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("getCount() returns parsed value when redis returns a number string")
    void getCount_returnsParsedValue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("pv:post:300")).thenReturn("12345");

        long count = new RedisStatsService(redisTemplate, jdbcTemplate, new RedisKeyNamespace("")).getCount(300L);

        assertEquals(12345L, count);
    }

    @Test
    void namespacedKeysAndFlushScanStayWithinTheRun() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        Cursor<String> scanCursor = cursor(List.of("bytedepth:it:r1:pv:post:12"));
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(scanCursor);
        when(values.get("bytedepth:it:r1:pv:post:12")).thenReturn("42");
        RedisStatsService service = new RedisStatsService(redisTemplate, jdbcTemplate,
                new RedisKeyNamespace("bytedepth:it:r1:"));

        service.increment(12L);
        assertEquals(42L, service.getCount(12L));
        service.flushToDB();

        verify(values).increment("bytedepth:it:r1:pv:post:12");
        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redisTemplate).scan(options.capture());
        assertEquals("bytedepth:it:r1:pv:post:*", options.getValue().getPattern());
        verify(jdbcTemplate).update(any(String.class), eq("/posts/12"), eq(42L),
                any(LocalDateTime.class), eq(42L), any(LocalDateTime.class));
    }

    @SuppressWarnings("unchecked")
    private static Cursor<String> cursor(List<String> values) {
        Cursor<String> cursor = mock(Cursor.class);
        Iterator<String> iterator = values.iterator();
        doAnswer(invocation -> iterator.hasNext()).when(cursor).hasNext();
        doAnswer(invocation -> iterator.next()).when(cursor).next();
        return cursor;
    }
}
