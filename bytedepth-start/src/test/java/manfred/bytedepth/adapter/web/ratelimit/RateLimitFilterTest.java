package manfred.bytedepth.adapter.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import manfred.bytedepth.app.ratelimit.RateLimitDecision;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private RateLimitPort rateLimitPort;
    private FilterChain filterChain;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimitPort = mock(RateLimitPort.class);
        filterChain = mock(FilterChain.class);
        filter = new RateLimitFilter(rateLimitPort, new RateLimitProperties());
        when(rateLimitPort.tryConsume(any(), anyLong(), any(), any())).thenReturn(RateLimitDecision.permit());
    }

    @Test
    void allowsProtectedRequestWhenAllBucketsHaveTokens() throws Exception {
        MockHttpServletRequest request = post("/login", "203.0.113.10");
        request.addParameter("username", "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitPort).tryConsume(eq("login-ip"), anyLong(), any(), eq("203.0.113.10"));
        verify(rateLimitPort).tryConsume(eq("login-username"), anyLong(), any(), eq("alice"));
    }

    @Test
    void returns429AndRoundsRetryAfterUpWhenBucketIsExhausted() throws Exception {
        when(rateLimitPort.tryConsume(eq("register-ip"), anyLong(), any(), any()))
                .thenReturn(RateLimitDecision.rejected(999_999_999));
        MockHttpServletRequest request = post("/register", "203.0.113.11");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("慢一点，休息一下", "id=\"countdown\">1");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void returnsAtLeastOneSecondForZeroAndNegativeRetryWaits() throws Exception {
        when(rateLimitPort.tryConsume(eq("register-ip"), anyLong(), any(), any()))
                .thenReturn(RateLimitDecision.rejected(0), RateLimitDecision.rejected(-1));

        MockHttpServletResponse zeroWait = new MockHttpServletResponse();
        MockHttpServletResponse negativeWait = new MockHttpServletResponse();
        filter.doFilter(post("/register", "203.0.113.11"), zeroWait, filterChain);
        filter.doFilter(post("/register", "203.0.113.11"), negativeWait, filterChain);

        assertThat(zeroWait.getHeader("Retry-After")).isEqualTo("1");
        assertThat(negativeWait.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void usesSeparateLoginUsernameIdentityKeys() throws Exception {
        MockHttpServletRequest first = post("/login", "203.0.113.12");
        first.addParameter("username", "alice");
        MockHttpServletRequest second = post("/login", "203.0.113.12");
        second.addParameter("username", "ALICE");

        filter.doFilter(first, new MockHttpServletResponse(), filterChain);
        filter.doFilter(second, new MockHttpServletResponse(), filterChain);

        ArgumentCaptor<String> identities = ArgumentCaptor.forClass(String.class);
        verify(rateLimitPort, org.mockito.Mockito.times(2))
                .tryConsume(eq("login-username"), anyLong(), any(), identities.capture());
        assertThat(identities.getAllValues()).containsExactly("alice", "alice");
    }

    @Test
    void appliesOnlyTheIpRuleWhenLoginUsernameIsMissingOrBlank() throws Exception {
        MockHttpServletRequest missing = post("/login", "203.0.113.12");
        MockHttpServletRequest blank = post("/login", "203.0.113.12");
        blank.addParameter("username", "  ");

        filter.doFilter(missing, new MockHttpServletResponse(), filterChain);
        filter.doFilter(blank, new MockHttpServletResponse(), filterChain);

        verify(rateLimitPort, org.mockito.Mockito.times(2))
                .tryConsume(eq("login-ip"), anyLong(), any(), eq("203.0.113.12"));
        verify(rateLimitPort, never()).tryConsume(eq("login-username"), anyLong(), any(), any());
    }

    @Test
    void usesNginxProvidedRealIpInsteadOfSpoofableForwardedFor() throws Exception {
        MockHttpServletRequest request = post("/register", "172.20.0.4");
        request.addHeader("X-Real-IP", "203.0.113.30");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.30");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(rateLimitPort).tryConsume(eq("register-ip"), anyLong(), any(), eq("203.0.113.30"));
    }

    @Test
    void failsOpenWhenRedisRateLimiterThrows() throws Exception {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(rateLimitPort).tryConsume(eq("comment-rating-ip"), anyLong(), any(), any());
        MockHttpServletRequest request = post("/posts/example/comments", "203.0.113.13");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void appliesRulesToCommentsRatingsUploadsAndReadingProgress() throws Exception {
        String[][] protectedRoutes = {
                {"/posts/example/comments", "comment-rating-ip"},
                {"/posts/example/rating", "comment-rating-ip"},
                {"/admin/images/upload", "upload-ip"},
                {"/posts/example/reading-progress", "reading-progress-ip"}
        };

        for (String[] route : protectedRoutes) {
            filter.doFilter(post(route[0], "203.0.113.20"), new MockHttpServletResponse(), filterChain);
        }
        verify(rateLimitPort, org.mockito.Mockito.times(2))
                .tryConsume(eq("comment-rating-ip"), anyLong(), any(), eq("203.0.113.20"));
        verify(rateLimitPort).tryConsume(eq("upload-ip"), anyLong(), any(), eq("203.0.113.20"));
        ArgumentCaptor<Long> readingProgressCapacity = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Duration> readingProgressPeriod = ArgumentCaptor.forClass(Duration.class);
        verify(rateLimitPort).tryConsume(eq("reading-progress-ip"), readingProgressCapacity.capture(),
                readingProgressPeriod.capture(), eq("203.0.113.20"));
        assertThat(readingProgressCapacity.getValue()).isEqualTo(30L);
        assertThat(readingProgressPeriod.getValue()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void resolvesContextPathAndFallsBackToRemoteAddressForBlankRealIp() throws Exception {
        MockHttpServletRequest request = post("/blog/posts/example/reading-progress", "203.0.113.21");
        request.setContextPath("/blog");
        request.addHeader("X-Real-IP", " ");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(rateLimitPort).tryConsume(eq("reading-progress-ip"), anyLong(), any(), eq("203.0.113.21"));
    }

    @Test
    void leavesRequestsOutsideTheContextPathUntouchedAndHandlesNullContextPath() throws Exception {
        MockHttpServletRequest outsideContext = post("/outside", "203.0.113.22");
        outsideContext.setContextPath("/blog");
        HttpServletRequest nullContext = mock(HttpServletRequest.class);
        when(nullContext.getMethod()).thenReturn("POST");
        when(nullContext.getContextPath()).thenReturn(null);
        when(nullContext.getRequestURI()).thenReturn("/outside");
        when(nullContext.getHeader("X-Real-IP")).thenReturn(null);
        when(nullContext.getRemoteAddr()).thenReturn("203.0.113.23");

        filter.doFilter(outsideContext, new MockHttpServletResponse(), filterChain);
        filter.doFilter(nullContext, new MockHttpServletResponse(), filterChain);

        verify(rateLimitPort, never()).tryConsume(any(), anyLong(), any(), any());
    }

    @Test
    void leavesPublicReadsAndOtherAdminRoutesUntouched() throws Exception {
        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/posts/example");
        MockHttpServletRequest admin = post("/admin/comments/1/approve", "203.0.113.14");

        filter.doFilter(read, new MockHttpServletResponse(), filterChain);
        filter.doFilter(admin, new MockHttpServletResponse(), filterChain);

        verify(rateLimitPort, never()).tryConsume(any(), anyLong(), any(), any());
    }

    private MockHttpServletRequest post(String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
