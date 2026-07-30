package manfred.bytedepth.infrastructure.ops;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisInfoParserTest {

    @Test
    void parse_readsRequestedMetricsAndIgnoresSectionHeadings() {
        Map<String, String> info = RedisInfoParser.parse("""
                # Memory
                used_memory_human:1.25M
                # Clients
                connected_clients:8
                # Stats
                keyspace_hits:91
                keyspace_misses:4
                malformed-line
                """);

        assertEquals("1.25M", info.get("used_memory_human"));
        assertEquals(8, RedisInfoParser.longValue(info, "connected_clients"));
        assertEquals(91, RedisInfoParser.longValue(info, "keyspace_hits"));
        assertEquals(4, RedisInfoParser.longValue(info, "keyspace_misses"));
    }

    @Test
    void longValue_usesZeroForMissingOrMalformedMetrics() {
        assertEquals(0, RedisInfoParser.longValue(Map.of(), "keyspace_hits"));
        assertEquals(0, RedisInfoParser.longValue(Map.of("keyspace_hits", "not-a-number"), "keyspace_hits"));
    }
}
