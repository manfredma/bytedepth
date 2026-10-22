package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Resolves and writes credentials through exactly one transport for each application route. */
public final class RouteSessionIdResolver implements HttpSessionIdResolver {
    private static final PathPattern MOBILE = PathPatternParser.defaultInstance.parse("/api/mobile/v1/**");

    private final HttpSessionIdResolver web;
    private final HttpSessionIdResolver mobile = new HeaderHttpSessionIdResolver("X-App-Session");

    public RouteSessionIdResolver(HttpSessionIdResolver web) {
        this.web = web;
    }

    public static boolean isMobileRequest(HttpServletRequest request) {
        return MOBILE.matches(pathWithinApplication(request));
    }

    public static PathContainer pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        // Spring decodes each segment for matching, without turning an encoded slash into a separator
        // or treating '+' as form-encoded whitespace. Session, security and quotas must agree with MVC.
        return PathContainer.parsePath(path);
    }

    private HttpSessionIdResolver delegate(HttpServletRequest request) {
        return isMobileRequest(request) ? mobile : web;
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        return delegate(request).resolveSessionIds(request);
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String id) {
        delegate(request).setSessionId(request, response, id);
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        delegate(request).expireSession(request, response);
    }
}
