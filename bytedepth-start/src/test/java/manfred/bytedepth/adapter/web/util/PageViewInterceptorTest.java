package manfred.bytedepth.adapter.web.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import manfred.bytedepth.domain.stats.PageViewedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
class PageViewInterceptorTest {

    @Mock
    private VisitRequestFilter visitRequestFilter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private org.springframework.mock.web.MockHttpServletRequest request =
            new org.springframework.mock.web.MockHttpServletRequest();

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private PageViewInterceptor interceptor;

    @Test
    void preHandle_nonHandlerMethod_skips() {
        Object nonHandler = new Object();
        boolean result = interceptor.preHandle(request, response, nonHandler);

        assertThat(result).isTrue();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void preHandle_filteredRequest_skips() {
        request.addHeader("User-Agent", "bot");
        when(visitRequestFilter.shouldRecord(any())).thenReturn(false);

        boolean result = interceptor.preHandle(request, response, mock(HandlerMethod.class));

        assertThat(result).isTrue();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void preHandle_validRequest_publishesEvent() {
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.addHeader("Referer", "https://google.com");
        request.setRemoteAddr("8.8.8.8");
        request.setRequestURI("/about");
        when(visitRequestFilter.shouldRecord(any())).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, mock(HandlerMethod.class));

        assertThat(result).isTrue();
        ArgumentCaptor<PageViewedEvent> captor = ArgumentCaptor.forClass(PageViewedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PageViewedEvent event = captor.getValue();
        assertThat(event.pagePath()).isEqualTo("/about");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.referer()).isEqualTo("https://google.com");
        assertThat(event.ip()).isEqualTo("8.8.8.8");
    }
}
