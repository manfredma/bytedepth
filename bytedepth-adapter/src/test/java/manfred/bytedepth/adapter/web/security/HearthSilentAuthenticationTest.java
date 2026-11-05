package manfred.bytedepth.adapter.web.security;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class HearthSilentAuthenticationTest {
    private final HearthSilentAuthentication silent = new HearthSilentAuthentication();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void callbackClassificationIsRequestScopedRatherThanSessionWide() {
        var start = new MockHttpServletRequest();
        assertTrue(silent.begin(start));
        assertTrue(silent.arm(start));
        var callback = new MockHttpServletRequest();
        callback.setSession(start.getSession());
        assertFalse(silent.pending(callback));
    }

    @Test
    void authenticationAndSuppressionAreIndependentOfBrowserIdentityHints() {
        assertFalse(silent.authenticated());
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertFalse(silent.authenticated());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("reader", null, List.of()));
        assertTrue(silent.authenticated());
        var request = new MockHttpServletRequest();
        assertFalse(silent.suppressed(request));
        request.setCookies(new Cookie("other", "1"), new Cookie("bytedepth-auth-logout", "0"));
        assertFalse(silent.suppressed(request));
        request.setCookies(new Cookie("bytedepth-auth-logout", "1"));
        assertTrue(silent.suppressed(request));
        assertFalse(silent.begin(request));
        request.setCookies();
        assertFalse(silent.begin(request));
    }

    @Test
    void eachSessionHasABoundedStartAndSingleUseArm() {
        var request = new MockHttpServletRequest();
        assertFalse(silent.arm(request));
        assertFalse(silent.pending(request));
        assertTrue(silent.begin(request));
        assertFalse(silent.pending(request));
        assertFalse(silent.begin(request));
        assertTrue(silent.arm(request));
        assertTrue(silent.pending(request));
        assertFalse(silent.arm(request));
        silent.clear(request);
        assertFalse(silent.pending(request));
        request.getSession().setAttribute(HearthSilentAuthentication.NEXT, 1L);
        assertTrue(silent.begin(request));
        request.getSession().setAttribute(HearthSilentAuthentication.NEXT, "invalid");
        assertTrue(silent.begin(request));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("reader", null, List.of()));
        assertFalse(silent.arm(request));
        SecurityContextHolder.clearContext();
        request.setCookies(new Cookie("bytedepth-auth-logout", "1"));
        assertFalse(silent.arm(request));
    }

    @Test
    void completionIsOnlyForKnownSilentAttemptsAndClearsTransientState() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        silent.clear(request);
        assertFalse(silent.complete(request, response, "anonymous"));
        assertTrue(silent.begin(request));
        assertTrue(silent.arm(request));
        assertTrue(silent.complete(request, response, "anonymous"));
        assertEquals("/auth/silent-complete?outcome=anonymous", response.getRedirectedUrl());
        assertFalse(silent.complete(request, new MockHttpServletResponse(), "success"));
        assertNull(request.getSession().getAttribute(HearthSilentAuthentication.PENDING));
    }

    @Test
    void logoutHintIsHostOnlySecureAndNotAnAuthenticationCredential() {
        var request = new MockHttpServletRequest();
        request.setSecure(true);
        var response = new MockHttpServletResponse();
        silent.logout(request, response);
        var cookie = response.getCookie("bytedepth-auth-logout");
        assertNotNull(cookie);
        assertEquals("1", cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
        assertEquals("Lax", cookie.getAttribute("SameSite"));
        assertEquals("/", cookie.getPath());
        assertNull(cookie.getDomain());
        assertEquals(2_592_000, cookie.getMaxAge());
        response = new MockHttpServletResponse();
        request.setSecure(false);
        silent.loggedIn(request, response);
        cookie = response.getCookie("bytedepth-auth-logout");
        assertEquals(0, cookie.getMaxAge());
        assertFalse(cookie.getSecure());
    }
}
