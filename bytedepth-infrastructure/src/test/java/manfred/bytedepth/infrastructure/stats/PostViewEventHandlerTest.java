package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.domain.stats.PostViewedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostViewEventHandlerTest {

    @Test
    @DisplayName("onPostViewed resolves geo and writes log via mapper")
    void onPostViewed_resolvesGeoAndWritesLog() {
        GeoIpService geoIpService = mock(GeoIpService.class);
        PostViewLogMapper postViewLogMapper = mock(PostViewLogMapper.class);
        when(geoIpService.resolve("1.2.3.4")).thenReturn(new GeoInfo("Japan", "Tokyo"));

        PostViewEventHandler handler = new PostViewEventHandler(geoIpService, postViewLogMapper);

        LocalDateTime occurredAt = LocalDateTime.of(2026, 1, 15, 10, 30, 0);
        PostViewedEvent event = new PostViewedEvent(
                42L, 7L, "1.2.3.4", "Mozilla/5.0", "https://example.com", "tok-abc", occurredAt);

        handler.onPostViewed(event);

        ArgumentCaptor<PostViewLogDO> captor = ArgumentCaptor.forClass(PostViewLogDO.class);
        verify(postViewLogMapper).upsertVisit(captor.capture());

        PostViewLogDO log = captor.getValue();
        assertEquals(42L, log.getPostId());
        assertEquals(7L, log.getUserId());
        assertEquals("1.2.3.4", log.getIp());
        assertEquals("Mozilla/5.0", log.getUserAgent());
        assertEquals("https://example.com", log.getReferer());
        assertEquals("Japan", log.getCountry());
        assertEquals("Tokyo", log.getCity());
        assertEquals(occurredAt, log.getVisitedAt());
        assertEquals("tok-abc", log.getVisitToken());
    }

    @Test
    @DisplayName("onPostViewed handles null userId (anonymous visitor)")
    void onPostViewed_handlesNullUserId() {
        GeoIpService geoIpService = mock(GeoIpService.class);
        PostViewLogMapper postViewLogMapper = mock(PostViewLogMapper.class);
        when(geoIpService.resolve("9.9.9.9")).thenReturn(GeoInfo.unknown());

        PostViewEventHandler handler = new PostViewEventHandler(geoIpService, postViewLogMapper);

        PostViewedEvent event = new PostViewedEvent(
                1L, null, "9.9.9.9", null, null, null, LocalDateTime.now());

        handler.onPostViewed(event);

        ArgumentCaptor<PostViewLogDO> captor = ArgumentCaptor.forClass(PostViewLogDO.class);
        verify(postViewLogMapper).upsertVisit(captor.capture());
        PostViewLogDO log = captor.getValue();
        assertNull(log.getUserId());
        assertEquals("", log.getCountry());
        assertEquals("", log.getCity());
    }

    @Test
    @DisplayName("onPostViewed swallows exception from mapper")
    void onPostViewed_swallowsException() {
        GeoIpService geoIpService = mock(GeoIpService.class);
        PostViewLogMapper postViewLogMapper = mock(PostViewLogMapper.class);
        when(geoIpService.resolve(any())).thenReturn(GeoInfo.unknown());
        when(postViewLogMapper.upsertVisit(any(PostViewLogDO.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        PostViewEventHandler handler = new PostViewEventHandler(geoIpService, postViewLogMapper);

        PostViewedEvent event = new PostViewedEvent(
                1L, null, "1.1.1.1", null, null, null, LocalDateTime.now());

        // Should not throw
        handler.onPostViewed(event);

        verify(postViewLogMapper).upsertVisit(any(PostViewLogDO.class));
    }
}
