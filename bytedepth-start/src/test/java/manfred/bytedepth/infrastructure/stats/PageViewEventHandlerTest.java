package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.domain.stats.PageViewedEvent;
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
class PageViewEventHandlerTest {

    @Mock
    private GeoIpService geoIpService;

    @Mock
    private PageViewLogMapper pageViewLogMapper;

    @InjectMocks
    private PageViewEventHandler handler;

    @Test
    void onPageViewed_savesAllFields() {
        var event = new PageViewedEvent("/about", 99L, "8.8.8.8", "Mozilla/5.0",
                "https://bytedepth.cn/", LocalDateTime.of(2026, 8, 10, 12, 0));
        when(geoIpService.resolve("8.8.8.8")).thenReturn(new GeoInfo("China", "Beijing"));

        handler.onPageViewed(event);

        ArgumentCaptor<PageViewLogDO> captor = ArgumentCaptor.forClass(PageViewLogDO.class);
        verify(pageViewLogMapper).insertLog(captor.capture());
        PageViewLogDO saved = captor.getValue();
        assertThat(saved.getPagePath()).isEqualTo("/about");
        assertThat(saved.getUserId()).isEqualTo(99L);
        assertThat(saved.getIp()).isEqualTo("8.8.8.8");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getReferer()).isEqualTo("https://bytedepth.cn/");
        assertThat(saved.getCountry()).isEqualTo("China");
        assertThat(saved.getCity()).isEqualTo("Beijing");
        assertThat(saved.getVisitedAt()).isEqualTo(LocalDateTime.of(2026, 8, 10, 12, 0));
    }

    @Test
    void onPageViewed_anonymousUser_savesNullUserId() {
        var event = new PageViewedEvent("/", null, "1.2.3.4", "curl/7.0", null, LocalDateTime.now());
        when(geoIpService.resolve("1.2.3.4")).thenReturn(GeoInfo.unknown());

        handler.onPageViewed(event);

        ArgumentCaptor<PageViewLogDO> captor = ArgumentCaptor.forClass(PageViewLogDO.class);
        verify(pageViewLogMapper).insertLog(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
        assertThat(captor.getValue().getCountry()).isEmpty();
    }

    @Test
    void onPageViewed_geoResolutionFails_stillSavesLog() {
        var event = new PageViewedEvent("/projects", null, "bad-ip", null, null, LocalDateTime.now());
        when(geoIpService.resolve("bad-ip")).thenReturn(GeoInfo.unknown());

        handler.onPageViewed(event);

        verify(pageViewLogMapper).insertLog(any(PageViewLogDO.class));
    }
}