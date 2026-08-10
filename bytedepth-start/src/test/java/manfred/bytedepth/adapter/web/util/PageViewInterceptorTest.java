package manfred.bytedepth.adapter.web.util;

import jakarta.servlet.http.HttpServletRequest;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PageViewInterceptorTest {

    @Mock
    private VisitRequestFilter visitRequestFilter;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private HttpServletRequest request;
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
        when(request.getHeader("User-Agent")).thenReturn("bot");
        when(visitRequestFilter.shouldRecord(any())).thenReturn(false);

        boolean result = interceptor.preHandle(request, response, mock(HandlerMethod.class));

        assertThat(result).isTrue();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void preHandle_validRequest_publishesEvent() {
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("Referer")).thenReturn("https://google.com");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("8.8.8.8");
        when(request.getRequestURI()).thenReturn("/about");
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