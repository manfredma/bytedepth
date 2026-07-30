package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsRedisPort;
import manfred.bytedepth.app.ops.OpsRedisStatusDTO;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class RedisOpsAdapter implements OpsRedisPort {

    static final String POST_VIEW_PREFIX = "pv:post:";
    static final String SESSION_PREFIX = "bytedepth:session:";

    private final StringRedisTemplate redisTemplate;

    public RedisOpsAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OpsRedisStatusDTO inspect() {
        Map<String, String> info = RedisInfoParser.parse(redisTemplate.execute(this::redisInfo));
        return new OpsRedisStatusDTO(
                true,
                info.getOrDefault("used_memory_human", "0B"),
                RedisInfoParser.longValue(info, "connected_clients"),
                RedisInfoParser.longValue(info, "keyspace_hits"),
                RedisInfoParser.longValue(info, "keyspace_misses"),
                scanCount(POST_VIEW_PREFIX),
                scanCount(SESSION_PREFIX));
    }

    private String redisInfo(RedisConnection connection) {
        Object result = connection.execute("INFO");
        if (result instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return result == null ? "" : result.toString();
    }

    long scanCount(String prefix) {
        return redisTemplate.execute((RedisCallback<Long>) connection -> scanCount(connection, prefix));
    }

    static long scanCount(RedisConnection connection, String prefix) {
        long count = 0;
        try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(prefix + "*").count(500).build())) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
            return count;
        }
    }
}
