package manfred.bytedepth.infrastructure.ops;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisOpsAdapterTest {

    @Test
    void scanCount_countsPostViewKeysWithThePostViewPrefixOnly() {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ListCursor(List.of(
                "pv:post:1".getBytes(), "pv:post:2".getBytes(), "pv:post:3".getBytes())));

        assertEquals(3, RedisOpsAdapter.scanCount(connection, RedisOpsAdapter.POST_VIEW_PREFIX));
        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(connection).scan(options.capture());
        assertEquals("pv:post:*", options.getValue().getPattern());
    }

    @Test
    void scanCount_countsSessionKeysWithTheSessionPrefixOnly() {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.scan(any(ScanOptions.class))).thenReturn(new ListCursor(List.of(
                "bytedepth:session:a".getBytes(), "bytedepth:session:b".getBytes())));

        assertEquals(2, RedisOpsAdapter.scanCount(connection, RedisOpsAdapter.SESSION_PREFIX));
        ArgumentCaptor<ScanOptions> options = ArgumentCaptor.forClass(ScanOptions.class);
        verify(connection).scan(options.capture());
        assertEquals("bytedepth:session:*", options.getValue().getPattern());
    }

    private static final class ListCursor implements Cursor<byte[]> {
        private final Iterator<byte[]> iterator;
        private long position;
        private boolean closed;

        private ListCursor(List<byte[]> values) {
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
        public byte[] next() {
            position++;
            return iterator.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
