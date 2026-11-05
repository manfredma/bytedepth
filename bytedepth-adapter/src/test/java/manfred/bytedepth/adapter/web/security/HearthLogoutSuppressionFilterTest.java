package manfred.bytedepth.adapter.web.security;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class HearthLogoutSuppressionFilterTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlySuppressedAuthenticatedSessionsAreInvalidated() throws Exception {
        for (boolean authenticated : new boolean[] {true, false}) {
            for (boolean sessionPresent : new boolean[] {true, false}) {
                var request = new MockHttpServletRequest();
                request.setCookies(new Cookie(HearthSilentAuthentication.LOGOUT_COOKIE, "1"));
                var session = sessionPresent ? request.getSession() : null;
                if (authenticated)
                    SecurityContextHolder.getContext()
                            .setAuthentication(
                                    new UsernamePasswordAuthenticationToken("reader", null, java.util.List.of()));
                new HearthLogoutSuppressionFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    assertNull(SecurityContextHolder.getContext().getAuthentication());
                    if (authenticated) assertNull(request.getSession(false));
                    else assertSame(session, request.getSession(false));
                });
                SecurityContextHolder.clearContext();
            }
        }
        var request = new MockHttpServletRequest();
        var session = request.getSession();
        new HearthLogoutSuppressionFilter()
                .doFilter(
                        request,
                        new MockHttpServletResponse(),
                        (req, res) -> assertSame(session, request.getSession(false)));
    }
}
