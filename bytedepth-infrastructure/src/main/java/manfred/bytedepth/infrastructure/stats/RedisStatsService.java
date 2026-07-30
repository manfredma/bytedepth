package manfred.bytedepth.infrastructure.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.domain.stats.PostViewCounter;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStatsService implements PostViewCounter {

    private static final String KEY_PREFIX = "pv:post:";
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void increment(Long postId) {
        redisTemplate.opsForValue().increment(KEY_PREFIX + postId);
    }

    @Override
    public long getCount(Long postId) {
        String val = redisTemplate.opsForValue().get(KEY_PREFIX + postId);
        return val == null ? 0 : Long.parseLong(val);
    }

    @Scheduled(fixedDelay = 300000)
    public void flushToDB() {
        long scanned = 0;
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build();
        try (Cursor<String> keys = redisTemplate.scan(options)) {
            while (keys.hasNext()) {
                String key = keys.next();
                scanned++;
                String postId = key.substring(KEY_PREFIX.length());
                String path = "/posts/" + postId;
                String val = redisTemplate.opsForValue().get(key);
                if (val == null) continue;
                long count = Long.parseLong(val);
                jdbcTemplate.update(
                        "INSERT INTO page_stats (path, pv_count, updated_at) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE pv_count = ?, updated_at = ?",
                        path, count, LocalDateTime.now(), count, LocalDateTime.now());
            }
        }
        log.debug("Stats flushed to DB: {} keys", scanned);
    }
}
