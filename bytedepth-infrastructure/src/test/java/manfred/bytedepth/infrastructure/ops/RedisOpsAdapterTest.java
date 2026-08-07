package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsRedisStatusDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisOpsAdapterTest {

    @Test
    void inspectReadsByteInfoAndCountsOnlyConfiguredKeyPrefixes() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.execute("INFO")).thenReturn(("used_memory_human:1.5M\nconnected_clients:2\n"
                + "keyspace_hits:3\nkeyspace_misses:4\n").getBytes(StandardCharsets.UTF_8));
        when(connection.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            ScanOptions options = invocation.getArgument(0);
            return new ListCursor(options.getPattern().startsWith(RedisOpsAdapter.POST_VIEW_PREFIX)
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
    void inspectConvertsNonByteInfoToStringAndUsesDefaultsForMissingFields() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.execute("INFO")).thenReturn("used_memory_human:2M\nconnected_clients:7\n");
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ListCursor(List.of()));
        executeCallbacksAgainst(template, connection);

        OpsRedisStatusDTO status = new RedisOpsAdapter(template).inspect();

        assertEquals("2M", status.usedMemoryHuman());
        assertEquals(7, status.connectedClients());
        assertEquals(0, status.keyspaceHits());
        assertEquals(0, status.keyspaceMisses());
    }

    @Test
    void inspectTreatsNullInfoAsEmpty() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.execute("INFO")).thenReturn(null);
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ListCursor(List.of()));
        executeCallbacksAgainst(template, connection);

        OpsRedisStatusDTO status = new RedisOpsAdapter(template).inspect();

        assertEquals("0B", status.usedMemoryHuman());
        assertEquals(0, status.connectedClients());
    }

    @Test
    void scanCountBuildsTheExpectedScanPattern() {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ListCursor(List.of(
                "pv:post:1".getBytes(), "pv:post:2".getBytes(), "pv:post:3".getBytes())));

        assertEquals(3, RedisOpsAdapter.scanCount(connection, RedisOpsAdapter.POST_VIEW_PREFIX));
        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(connection).scan(options.capture());
        assertEquals("pv:post:*", options.getValue().getPattern());
    }

    private static void executeCallbacksAgainst(StringRedisTemplate template, RedisConnection connection) {
        doAnswer(invocation -> ((RedisCallback<?>) invocation.getArgument(0)).doInRedis(connection))
                .when(template).execute(any(RedisCallback.class));
    }

    private static final class ListCursor implements Cursor<byte[]> {
        private final Iterator<byte[]> iterator;
        private long position;
        private boolean closed;

        private ListCursor(List<byte[]> values) {
            this.iterator = values.iterator();
        }

        @Override public org.springframework.data.redis.core.Cursor.CursorId getId() { return org.springframework.data.redis.core.Cursor.CursorId.initial(); }
        @Override public long getCursorId() { return 0; }
        @Override public boolean isClosed() { return closed; }
        @Override public long getPosition() { return position; }
        @Override public boolean hasNext() { return iterator.hasNext(); }
        @Override public byte[] next() { position++; return iterator.next(); }
        @Override public void close() { closed = true; }
    }
}
