package manfred.bytedepth.adapter.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private RateLimitService rateLimitService;
    private FilterChain filterChain;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        filterChain = mock(FilterChain.class);
        filter = new RateLimitFilter(rateLimitService, new RateLimitProperties());
        when(rateLimitService.tryConsume(any(), any(), any())).thenReturn(RateLimitDecision.permit());
    }

    @Test
    void allowsProtectedRequestWhenAllBucketsHaveTokens() throws Exception {
        MockHttpServletRequest request = post("/login", "203.0.113.10");
        request.addParameter("username", "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitService).tryConsume(eq("login-ip"), any(), eq("203.0.113.10"));
        verify(rateLimitService).tryConsume(eq("login-username"), any(), eq("alice"));
    }

    @Test
    void returns429AndRoundsRetryAfterUpWhenBucketIsExhausted() throws Exception {
        when(rateLimitService.tryConsume(eq("register-ip"), any(), any()))
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
    void usesSeparateLoginUsernameIdentityKeys() throws Exception {
        MockHttpServletRequest first = post("/login", "203.0.113.12");
        first.addParameter("username", "alice");
        MockHttpServletRequest second = post("/login", "203.0.113.12");
        second.addParameter("username", "ALICE");

        filter.doFilter(first, new MockHttpServletResponse(), filterChain);
        filter.doFilter(second, new MockHttpServletResponse(), filterChain);

        ArgumentCaptor<String> identities = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService, org.mockito.Mockito.times(2))
                .tryConsume(eq("login-username"), any(), identities.capture());
        assertThat(identities.getAllValues()).containsExactly("alice", "alice");
    }

    @Test
    void usesNginxProvidedRealIpInsteadOfSpoofableForwardedFor() throws Exception {
        MockHttpServletRequest request = post("/register", "172.20.0.4");
        request.addHeader("X-Real-IP", "203.0.113.30");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.30");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(rateLimitService).tryConsume(eq("register-ip"), any(), eq("203.0.113.30"));
    }

    @Test
    void failsOpenWhenRedisRateLimiterThrows() throws Exception {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(rateLimitService).tryConsume(eq("comment-rating-ip"), any(), any());
        MockHttpServletRequest request = post("/posts/example/comments", "203.0.113.13");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void appliesTheSharedRuleToCommentsRatingsAndUploadsOnly() throws Exception {
        String[][] protectedRoutes = {
                {"/posts/example/comments", "comment-rating-ip"},
                {"/posts/example/rating", "comment-rating-ip"},
                {"/admin/images/upload", "upload-ip"}
        };

        for (String[] route : protectedRoutes) {
            filter.doFilter(post(route[0], "203.0.113.20"), new MockHttpServletResponse(), filterChain);
        }
        verify(rateLimitService, org.mockito.Mockito.times(2))
                .tryConsume(eq("comment-rating-ip"), any(), eq("203.0.113.20"));
        verify(rateLimitService).tryConsume(eq("upload-ip"), any(), eq("203.0.113.20"));
    }

    @Test
    void leavesPublicReadsAndOtherAdminRoutesUntouched() throws Exception {
        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/posts/example");
        MockHttpServletRequest admin = post("/admin/comments/1/approve", "203.0.113.14");

        filter.doFilter(read, new MockHttpServletResponse(), filterChain);
        filter.doFilter(admin, new MockHttpServletResponse(), filterChain);

        verify(rateLimitService, never()).tryConsume(any(), any(), any());
    }

    private MockHttpServletRequest post(String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
