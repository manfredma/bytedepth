package manfred.bytedepth.adapter.web.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/** Browser-local reading preference; this is an analytics opt-out, never an authorization signal. */
public class ReadingPrivacy implements HandlerInterceptor {
    private static final String COOKIE = "bd_reading_mode";

    public static boolean isPrivate(HttpServletRequest request) {
        String query = request.getParameter("bd_traffic");
        if ("normal".equals(query)) {
            return false;
        }
        return "verification".equals(query) || hasPrivateCookie(request);
    }

    public static boolean isExcluded(HttpServletRequest request) {
        return "verification".equals(request.getHeader("X-Bytedepth-Traffic")) || isPrivate(request);
    }

    private static boolean hasPrivateCookie(HttpServletRequest request) {
        String mode = WebUtils.readCookie(request, COOKIE);
        return "private".equals(mode) || "verification".equals(mode);
    }

    public static void setPreference(HttpServletRequest request, HttpServletResponse response, boolean enabled) {
        writeCookie(request, response, enabled ? "private" : "", enabled ? -1 : 0);
    }

    private static void writeCookie(
            HttpServletRequest request, HttpServletResponse response, String value, int maxAge) {
        // This non-sensitive preference is readable so already-open tabs can stop local tracking too.
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(COOKIE, value)
                        .path("/")
                        .secure(request.isSecure())
                        .sameSite("Lax")
                        .maxAge(maxAge)
                        .build()
                        .toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String query = request.getParameter("bd_traffic");
        if ("verification".equals(query)) {
            // An automated verification must not shorten a user's manually selected session preference.
            if (!"private".equals(WebUtils.readCookie(request, COOKIE))) {
                writeCookie(request, response, "verification", 1800);
            }
        } else if ("normal".equals(query)) {
            setPreference(request, response, false);
        }
        if (isExcluded(request)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        }
        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView model) {
        if (model != null && "public/reading-history".equals(model.getViewName())) {
            model.addObject("privateReading", isPrivate(request));
        }
    }
}
