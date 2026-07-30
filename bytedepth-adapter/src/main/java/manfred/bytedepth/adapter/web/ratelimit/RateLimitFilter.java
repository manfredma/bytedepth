package manfred.bytedepth.adapter.web.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies the small set of public write quotas before authentication or controller execution. */
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        List<Attempt> attempts = matchingAttempts(request);
        for (Attempt attempt : attempts) {
            try {
                RateLimitDecision decision = rateLimitService.tryConsume(attempt.ruleName(), attempt.rule(), attempt.identity());
                if (!decision.allowed()) {
                    reject(response, decision.nanosToWaitForRefill());
                    return;
                }
            } catch (RuntimeException e) {
                // Rate limiting is deliberately fail-open: a Redis blip must not take down writes.
                log.warn("Redis 限流不可用，放行请求: rule={}, path={}, cause={}",
                        attempt.ruleName(), request.getRequestURI(), e.toString());
            }
        }
        filterChain.doFilter(request, response);
    }

    private List<Attempt> matchingAttempts(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return List.of();
        }
        String path = pathWithinApplication(request);
        String ip = clientIp(request);
        if ("/login".equals(path)) {
            List<Attempt> attempts = new ArrayList<>();
            attempts.add(new Attempt("login-ip", properties.getLoginIp(), ip));
            String username = request.getParameter("username");
            if (username != null && !username.isBlank()) {
                attempts.add(new Attempt("login-username", properties.getLoginUsername(),
                        username.trim().toLowerCase(Locale.ROOT)));
            }
            return attempts;
        }
        if ("/register".equals(path)) {
            return List.of(new Attempt("register-ip", properties.getRegisterIp(), ip));
        }
        if (path.matches("/posts/[^/]+/(comments|rating)")) {
            return List.of(new Attempt("comment-rating-ip", properties.getCommentRatingIp(), ip));
        }
        if ("/admin/images/upload".equals(path)) {
            return List.of(new Attempt("upload-ip", properties.getUploadIp(), ip));
        }
        return List.of();
    }

    private void reject(HttpServletResponse response, long nanosToWait) throws IOException {
        long retryAfterSeconds = Math.max(0, Duration.ofNanos(Math.max(0, nanosToWait)).toSeconds());
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
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

    private record Attempt(String ruleName, RateLimitProperties.Rule rule, String identity) {
    }
}
