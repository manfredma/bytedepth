package manfred.bytedepth.domain.stats;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class PostViewedEventTest {

    @Test
    void constructor_setsAllFields() {
        var now = LocalDateTime.of(2026, 1, 1, 12, 0);
        var event = new PostViewedEvent(1L, 42L, "1.2.3.4", "Mozilla/5.0", "https://google.com", "visit-token", now);

        assertEquals(1L, event.postId());
        assertEquals(42L, event.userId());
        assertEquals("1.2.3.4", event.ip());
        assertEquals("Mozilla/5.0", event.userAgent());
        assertEquals("https://google.com", event.referer());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void constructor_allowsNullUserId_forAnonymous() {
        var event = new PostViewedEvent(1L, null, "1.2.3.4", null, null, "visit-token", LocalDateTime.now());
        assertNull(event.userId());
    }
}
