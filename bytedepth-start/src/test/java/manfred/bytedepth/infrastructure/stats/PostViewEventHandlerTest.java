package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.domain.stats.PostViewedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostViewEventHandlerTest {

    @Mock
    private GeoIpService geoIpService;

    @Mock
    private PostViewLogMapper postViewLogMapper;

    @InjectMocks
    private PostViewEventHandler handler;

    @Test
    void onPostViewed_loggedInUser_savesAllFields() {
        var event = new PostViewedEvent(
                10L, 99L, "8.8.8.8", "Mozilla/5.0", "https://google.com",
                LocalDateTime.of(2026, 6, 29, 12, 0));
        when(geoIpService.resolve("8.8.8.8")).thenReturn(new GeoInfo("China", "Beijing"));

        handler.onPostViewed(event);

        ArgumentCaptor<PostViewLogDO> captor = ArgumentCaptor.forClass(PostViewLogDO.class);
        verify(postViewLogMapper).insert(captor.capture());
        PostViewLogDO saved = captor.getValue();
        assertThat(saved.getPostId()).isEqualTo(10L);
        assertThat(saved.getUserId()).isEqualTo(99L);
        assertThat(saved.getIp()).isEqualTo("8.8.8.8");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getReferer()).isEqualTo("https://google.com");
        assertThat(saved.getCountry()).isEqualTo("China");
        assertThat(saved.getCity()).isEqualTo("Beijing");
        assertThat(saved.getVisitedAt()).isEqualTo(LocalDateTime.of(2026, 6, 29, 12, 0));
    }

    @Test
    void onPostViewed_anonymousUser_savesNullUserId() {
        var event = new PostViewedEvent(5L, null, "1.2.3.4", "curl/7.0", null,
                LocalDateTime.now());
        when(geoIpService.resolve("1.2.3.4")).thenReturn(GeoInfo.unknown());

        handler.onPostViewed(event);

        ArgumentCaptor<PostViewLogDO> captor = ArgumentCaptor.forClass(PostViewLogDO.class);
        verify(postViewLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getCountry()).isEmpty();
    }

    @Test
    void onPostViewed_geoResolutionFails_stillSavesLog() {
        var event = new PostViewedEvent(1L, null, "bad-ip", null, null, LocalDateTime.now());
        when(geoIpService.resolve("bad-ip")).thenReturn(GeoInfo.unknown());

        handler.onPostViewed(event);

        verify(postViewLogMapper).insert(any(PostViewLogDO.class));
    }
}
