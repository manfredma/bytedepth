package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

/** Resolves and writes credentials through exactly one transport for each application route. */
public final class RouteSessionIdResolver implements HttpSessionIdResolver {
    private final HttpSessionIdResolver web;
    private final HttpSessionIdResolver mobile = new HeaderHttpSessionIdResolver("X-App-Session");

    public RouteSessionIdResolver(HttpSessionIdResolver web) {
        this.web = web;
    }

    public static boolean isMobileRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path.equals("/api/mobile/v1") || path.startsWith("/api/mobile/v1/");
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
