package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsRedisStatusDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Iterator;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisOpsAdapterTest {

    @Test
    void inspectReadsInfoAndCountsOnlyConfiguredKeyPrefixes() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class, RETURNS_DEEP_STUBS);
        Properties properties = properties("1.5M", "2", "3", "4");
        when(connection.serverCommands().info()).thenReturn(properties);
        when(connection.keyCommands().scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            ScanOptions options = invocation.getArgument(0);
            return cursor(options.getPattern().startsWith(RedisOpsAdapter.POST_VIEW_PREFIX)
                    ? List.of("pv:post:1".getBytes(), "pv:post:2".getBytes())
                    : List.of("bytedepth:session:a".getBytes()));
        });
        executeCallbacksAgainst(template, connection);

        OpsRedisStatusDTO status = new RedisOpsAdapter(template).inspect();

        assertEquals("1.5M", status.usedMemoryHuman());
        assertEquals(2, status.connectedClients());
        assertEquals(3, status.keyspaceHits());
        assertEquals(4, status.keyspaceMisses());
        assertEquals(2, status.postViewKeyCount());
        assertEquals(1, status.sessionKeyCount());
    }

    @Test
    void inspectUsesDefaultsForMissingInfoFields() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class, RETURNS_DEEP_STUBS);
        Cursor<byte[]> emptyCursor = cursor(List.of());
        when(connection.serverCommands().info()).thenReturn(properties("2M", "7", null, null));
        when(connection.keyCommands().scan(any(ScanOptions.class))).thenReturn(emptyCursor);
        executeCallbacksAgainst(template, connection);

        OpsRedisStatusDTO status = new RedisOpsAdapter(template).inspect();

        assertEquals("2M", status.usedMemoryHuman());
        assertEquals(7, status.connectedClients());
        assertEquals(0, status.keyspaceHits());
        assertEquals(0, status.keyspaceMisses());
    }

    @Test
    void inspectTreatsEmptyInfoAsEmpty() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class, RETURNS_DEEP_STUBS);
        Cursor<byte[]> emptyCursor = cursor(List.of());
        when(connection.serverCommands().info()).thenReturn(new Properties());
        when(connection.keyCommands().scan(any(ScanOptions.class))).thenReturn(emptyCursor);
        executeCallbacksAgainst(template, connection);

        OpsRedisStatusDTO status = new RedisOpsAdapter(template).inspect();

        assertEquals("0B", status.usedMemoryHuman());
        assertEquals(0, status.connectedClients());
    }

    @Test
    void scanCountBuildsTheExpectedScanPattern() {
        RedisConnection connection = mock(RedisConnection.class, RETURNS_DEEP_STUBS);
        Cursor<byte[]> scanCursor = cursor(List.of(
                "pv:post:1".getBytes(), "pv:post:2".getBytes(), "pv:post:3".getBytes()));
        when(connection.keyCommands().scan(any(ScanOptions.class))).thenReturn(scanCursor);

        assertEquals(3, RedisOpsAdapter.scanCount(connection, RedisOpsAdapter.POST_VIEW_PREFIX));
        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(connection.keyCommands()).scan(options.capture());
        assertEquals("pv:post:*", options.getValue().getPattern());
    }

    @SuppressWarnings("unchecked")
    private static void executeCallbacksAgainst(StringRedisTemplate template, RedisConnection connection) {
        doAnswer(invocation -> ((RedisCallback<?>) invocation.getArgument(0)).doInRedis(connection))
                .when(template).execute(any(RedisCallback.class));
    }

    private static Properties properties(String memory, String clients, String hits, String misses) {
        Properties properties = new Properties();
        properties.setProperty("used_memory_human", memory);
        properties.setProperty("connected_clients", clients);
        if (hits != null) {
            properties.setProperty("keyspace_hits", hits);
        }
        if (misses != null) {
            properties.setProperty("keyspace_misses", misses);
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Cursor<byte[]> cursor(List<byte[]> values) {
        Cursor<byte[]> cursor = mock(Cursor.class);
        Iterator<byte[]> iterator = values.iterator();
        doAnswer(invocation -> iterator.hasNext()).when(cursor).hasNext();
        doAnswer(invocation -> iterator.next()).when(cursor).next();
        return cursor;
    }
}
