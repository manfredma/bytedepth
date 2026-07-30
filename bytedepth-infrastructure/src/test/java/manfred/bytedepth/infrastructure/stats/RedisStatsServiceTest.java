package manfred.bytedepth.infrastructure.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
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
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(new ListCursor(List.of("pv:post:12", "pv:post:13")));
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("pv:post:12")).thenReturn("42");
        when(values.get("pv:post:13")).thenReturn(null);

        new RedisStatsService(redisTemplate, jdbcTemplate).flushToDB();

        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(redisTemplate).scan(options.capture());
        org.junit.jupiter.api.Assertions.assertEquals("pv:post:*", options.getValue().getPattern());
        verify(jdbcTemplate).update(
                eq("INSERT INTO page_stats (path, pv_count, updated_at) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE pv_count = ?, updated_at = ?"),
                eq("/posts/12"), eq(42L), any(LocalDateTime.class), eq(42L), any(LocalDateTime.class));
        verify(jdbcTemplate, never()).update(
                any(String.class), eq("/posts/13"), any(), any(), any(), any());
    }

    private static final class ListCursor implements Cursor<String> {
        private final Iterator<String> iterator;
        private long position;
        private boolean closed;

        private ListCursor(List<String> values) {
            this.iterator = values.iterator();
        }

        @Override
        public long getCursorId() {
            return 0;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public long getPosition() {
            return position;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public String next() {
            position++;
            return iterator.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
