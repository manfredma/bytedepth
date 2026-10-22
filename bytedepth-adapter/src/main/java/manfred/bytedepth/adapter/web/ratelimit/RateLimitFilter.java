package manfred.bytedepth.adapter.web.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.adapter.web.mobile.MobileApiError;
import manfred.bytedepth.adapter.web.security.RouteSessionIdResolver;
import manfred.bytedepth.app.ratelimit.RateLimitDecision;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Applies the small set of public write quotas before authentication or controller execution. */
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final RateLimitProperties.Rule READING_PROGRESS_RULE =
            new RateLimitProperties.Rule(30, Duration.ofMinutes(1));
    private static final String RATE_LIMIT_TEMPLATE = "classpath:rate-limit-page.html";

    private static final PathPattern WEB_LOGIN = PathPatternParser.defaultInstance.parse("/login");
    private static final PathPattern MOBILE_LOGIN = PathPatternParser.defaultInstance.parse("/api/mobile/v1/login");
    private static final PathPattern WEB_REGISTER = PathPatternParser.defaultInstance.parse("/register");
    private static final PathPattern MOBILE_REGISTER =
            PathPatternParser.defaultInstance.parse("/api/mobile/v1/registrations");
    private static final PathPattern MOBILE_RATING =
            PathPatternParser.defaultInstance.parse("/api/mobile/v1/posts/{postId}/rating");
    private static final PathPattern MOBILE_CREATE =
            PathPatternParser.defaultInstance.parse("/api/mobile/v1/posts/{postId}/{action:comments|annotations}");
    private static final PathPattern MOBILE_ANNOTATION =
            PathPatternParser.defaultInstance.parse("/api/mobile/v1/posts/{postId}/annotations/{annotationId}");
    private static final PathPattern WEB_INTERACTION =
            PathPatternParser.defaultInstance.parse("/posts/{slug}/{action:comments|rating}");
    private static final PathPattern WEB_READING =
            PathPatternParser.defaultInstance.parse("/posts/{slug}/{action:reading-progress|reading-events}");
    private static final PathPattern WEB_UPLOAD = PathPatternParser.defaultInstance.parse("/admin/images/upload");

    private final RateLimitPort rateLimitPort;
    private final RateLimitProperties properties;
    private final ResourceLoader resourceLoader;

    private volatile String cachedTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        List<Attempt> attempts = matchingAttempts(request);
        for (Attempt attempt : attempts) {
            if (attempt.rule() == null) {
                continue;
            }
            try {
                RateLimitDecision decision = rateLimitPort.tryConsume(
                        attempt.ruleName(),
                        attempt.rule().getCapacity(),
                        attempt.rule().getPeriod(),
                        attempt.identity());
                if (!decision.allowed()) {
                    reject(request, response, decision.nanosToWaitForRefill());
                    return;
                }
            } catch (RuntimeException e) {
                // Rate limiting is deliberately fail-open: a Redis blip must not take down writes.
                log.warn(
                        "Redis 限流不可用，放行请求: rule={}, path={}, cause={}",
                        attempt.ruleName(),
                        request.getRequestURI(),
                        e.toString());
            }
        }
        filterChain.doFilter(request, response);
    }

    private List<Attempt> matchingAttempts(HttpServletRequest request) {
        var path = RouteSessionIdResolver.pathWithinApplication(request);
        String ip = clientIp(request);
        if (RouteSessionIdResolver.isMobileRequest(request)) {
            String method = request.getMethod();
            if (("PUT".equals(method) && MOBILE_RATING.matches(path))
                    || ("POST".equals(method) && MOBILE_CREATE.matches(path))
                    || (("PATCH".equals(method) || "DELETE".equals(method)) && MOBILE_ANNOTATION.matches(path))) {
                return List.of(new Attempt("comment-rating-ip", properties.getCommentRatingIp(), ip));
            }
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return List.of();
        }
        if (WEB_LOGIN.matches(path) || MOBILE_LOGIN.matches(path)) {
            List<Attempt> attempts = new ArrayList<>();
            attempts.add(new Attempt("login-ip", properties.getLoginIp(), ip));
            String username = request.getParameter("username");
            if (username != null && !username.isBlank()) {
                attempts.add(new Attempt(
                        "login-username",
                        properties.getLoginUsername(),
                        username.trim().toLowerCase(Locale.ROOT)));
            }
            return attempts;
        }
        if (WEB_REGISTER.matches(path) || MOBILE_REGISTER.matches(path)) {
            return List.of(new Attempt("register-ip", properties.getRegisterIp(), ip));
        }
        if (WEB_INTERACTION.matches(path)) {
            return List.of(new Attempt("comment-rating-ip", properties.getCommentRatingIp(), ip));
        }
        if (WEB_READING.matches(path)) {
            return List.of(new Attempt("reading-progress-ip", READING_PROGRESS_RULE, ip));
        }
        if (WEB_UPLOAD.matches(path)) {
            return List.of(new Attempt("upload-ip", properties.getUploadIp(), ip));
        }
        return List.of();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long nanosToWait) throws IOException {
        long nonNegativeNanos = Math.max(0, nanosToWait);
        long retryAfterSeconds = nonNegativeNanos / 1_000_000_000L;
        if (nonNegativeNanos % 1_000_000_000L != 0) {
            retryAfterSeconds++;
        }
        retryAfterSeconds = Math.max(1, retryAfterSeconds);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setHeader("Cache-Control", "no-store");
        if (RouteSessionIdResolver.isMobileRequest(request)) {
            MobileApiError.write(response, 429, "RATE_LIMITED", "请求过于频繁，请稍后重试");
            return;
        }
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(rateLimitPage(retryAfterSeconds));
    }

    private String rateLimitPage(long retryAfterSeconds) throws IOException {
        if (cachedTemplate == null) {
            try (var is = resourceLoader.getResource(RATE_LIMIT_TEMPLATE).getInputStream()) {
                cachedTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return cachedTemplate.replace("{{retryAfterSeconds}}", Long.toString(retryAfterSeconds));
    }

    private String clientIp(HttpServletRequest request) {
        // Nginx overwrites X-Real-IP with its peer address. Do not trust X-Forwarded-For:
        // nginx's proxy_add_x_forwarded_for intentionally preserves a client-supplied value.
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private record Attempt(String ruleName, RateLimitProperties.Rule rule, String identity) {}
}
