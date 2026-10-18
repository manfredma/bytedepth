package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/** Public resource responses may be cached; controller responses retain Security's no-store default. */
public final class StaticResourceCacheHeadersWriter implements HeaderWriter {
    private static final Set<Integer> CACHEABLE_STATUSES = Set.of(200, 206, 304);
    private static final Pattern FINGERPRINTED =
            Pattern.compile("^/(?:css|js|vendor|fonts|icons)/.+-[a-f0-9]{32}\\.(?:css|js|woff2?|ttf|otf|svg|png|ico)$");
    private static final String IMMUTABLE =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable().getHeaderValue();

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        // Check the resolved handler and final status, not merely a URL suffix: controller
        // endpoints and rejected/obsolete fingerprints must never receive a public cache policy.
        if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
                        instanceof ResourceHttpRequestHandler)
                || !CACHEABLE_STATUSES.contains(response.getStatus())) {
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                FINGERPRINTED.matcher(path).matches()
                        ? IMMUTABLE
                        : CacheControl.noCache().getHeaderValue());
        response.setHeader(HttpHeaders.PRAGMA, null);
        response.setHeader(HttpHeaders.EXPIRES, null);
    }
}
