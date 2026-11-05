package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;

/** 仅保存短期流程状态；退出 Cookie 不具备任何认证能力。 */
public final class HearthSilentAuthentication {
    static final String ARMED = HearthSilentAuthentication.class.getName() + ".armed";
    static final String PENDING = HearthSilentAuthentication.class.getName() + ".pending";
    static final String INTERACTIVE = HearthSilentAuthentication.class.getName() + ".interactive";
    static final String NEXT = HearthSilentAuthentication.class.getName() + ".next";
    static final String LOGOUT_COOKIE = "bytedepth-auth-logout";

    boolean authenticated() {
        return new AuthenticationTrustResolverImpl()
                .isAuthenticated(SecurityContextHolder.getContext().getAuthentication());
    }

    boolean suppressed(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies != null) {
            for (var cookie : cookies) {
                if (LOGOUT_COOKIE.equals(cookie.getName()) && "1".equals(cookie.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean begin(HttpServletRequest request) {
        if (authenticated() || suppressed(request) || interactive(request)) {
            return false;
        }
        var session = request.getSession();
        var next = session.getAttribute(NEXT);
        if (next instanceof Long time && time > System.currentTimeMillis()) {
            return false;
        }
        session.setAttribute(NEXT, System.currentTimeMillis() + 300_000L);
        session.setAttribute(ARMED, Boolean.TRUE);
        return true;
    }

    boolean arm(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null
                || !Boolean.TRUE.equals(session.getAttribute(ARMED))
                || authenticated()
                || suppressed(request)
                || interactive(request)) {
            return false;
        }
        session.removeAttribute(ARMED);
        request.setAttribute(PENDING, Boolean.TRUE);
        return true;
    }

    boolean pending(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(PENDING));
    }

    void clear(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(ARMED);
            request.removeAttribute(PENDING);
        }
    }

    boolean interactive(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null
                && session.getAttribute(INTERACTIVE) instanceof Long until
                && until > System.currentTimeMillis();
    }

    void interactiveStarted(HttpServletRequest request) {
        clear(request);
        request.getSession().setAttribute(INTERACTIVE, System.currentTimeMillis() + 300_000L);
    }

    boolean complete(HttpServletRequest request, HttpServletResponse response, String outcome) throws IOException {
        if (!pending(request)) {
            return false;
        }
        clear(request);
        response.sendRedirect("/auth/silent-complete?outcome=" + outcome);
        return true;
    }

    void logout(HttpServletRequest request, HttpServletResponse response) {
        cookie(request, response, "1", 30 * 24 * 60 * 60);
    }

    void loggedIn(HttpServletRequest request, HttpServletResponse response) {
        cookie(request, response, "", 0);
    }

    private void cookie(HttpServletRequest request, HttpServletResponse response, String value, int age) {
        var cookie = new Cookie(LOGOUT_COOKIE, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setAttribute("SameSite", "Lax");
        cookie.setMaxAge(age);
        response.addCookie(cookie);
    }
}
