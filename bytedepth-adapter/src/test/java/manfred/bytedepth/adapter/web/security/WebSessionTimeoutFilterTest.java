package manfred.bytedepth.adapter.web.security;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebSessionTimeoutFilterTest {
    @Test
    void anonymousRequestsDoNotCreateSessionButNewSessionsHaveTimeoutBeforeRedirect() throws Exception {
        var filter = new WebSessionTimeoutFilter(600);
        var request = new MockHttpServletRequest();
        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (req, res) -> assertNull(((HttpServletRequest) req).getSession(false)));
        assertNull(request.getSession(false));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            var session = ((HttpServletRequest) req).getSession();
            assertEquals(600, session.getMaxInactiveInterval());
            assertEquals(600, ((HttpServletRequest) req).getSession(false).getMaxInactiveInterval());
            ((jakarta.servlet.http.HttpServletResponse) res).sendRedirect("/");
        });
    }
}
