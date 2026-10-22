package manfred.bytedepth.adapter.web.mobile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;

class MobileDispatchExceptionResolverTest {
    private final MobileDispatchExceptionResolver resolver = new MobileDispatchExceptionResolver();

    @Test
    void unmatchedRouteReturnsJsonAndDomainExceptionsRemainWithAdvice() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/mobile/v1/posts/unmapped");
        var response = new MockHttpServletResponse();
        assertThat(resolver.resolveException(
                        request,
                        response,
                        null,
                        new NoHandlerFoundException("GET", request.getRequestURI(), new HttpHeaders())))
                .isNotNull();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("\"code\":\"NOT_FOUND\"");
        var untouched = new MockHttpServletResponse();
        assertThat(resolver.resolveException(request, untouched, null, new IllegalArgumentException("domain")))
                .isNull();
        assertThat(untouched.getContentAsString()).isEmpty();
    }

    @Test
    void unknownSupportedMethodsDoNotInventAllowHeader() {
        var response = new MockHttpServletResponse();
        resolver.resolveException(
                new MockHttpServletRequest("POST", "/api/mobile/v1/me"),
                response,
                null,
                new HttpRequestMethodNotSupportedException("POST"));
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isNull();
    }

    @Test
    void disconnectedClientDoesNotFallThroughToAnHtmlError() throws Exception {
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(response.getWriter()).thenThrow(new IOException("disconnected"));
        var logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(MobileDispatchExceptionResolver.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        logger.addAppender(events);
        events.start();
        try {
            assertThat(resolver.resolveException(
                            new MockHttpServletRequest("POST", "/api/mobile/v1/me"),
                            response,
                            null,
                            new HttpRequestMethodNotSupportedException("POST")))
                    .isNotNull();
            assertThat(events.list)
                    .singleElement()
                    .satisfies(event ->
                            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("disconnected"));
        } finally {
            logger.detachAppender(events);
            logger.setAdditive(additive);
        }
    }
}
