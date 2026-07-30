package manfred.bytedepth.adapter.web.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
        long nonNegativeNanos = Math.max(0, nanosToWait);
        long retryAfterSeconds = nonNegativeNanos / 1_000_000_000L;
        if (nonNegativeNanos % 1_000_000_000L != 0) {
            retryAfterSeconds++;
        }
        retryAfterSeconds = Math.max(1, retryAfterSeconds);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(rateLimitPage(retryAfterSeconds));
    }

    private String rateLimitPage(long retryAfterSeconds) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>请稍后再试 · ByteDepth</title>
                  <style>
                    :root { color-scheme: dark; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                    * { box-sizing: border-box; }
                    body { align-items: center; background: #17172b; color: #f8f8fc; display: flex; justify-content: center; margin: 0; min-height: 100vh; padding: 24px; }
                    main { background: #252542; border: 1px solid #454568; border-radius: 28px; box-shadow: 0 24px 64px #08081288; max-width: 480px; padding: 44px 36px; text-align: center; width: 100%; }
                    .icon { background: #39395d; border-radius: 50%; display: inline-grid; font-size: 30px; height: 66px; place-items: center; width: 66px; }
                    h1 { font-size: 28px; margin: 20px 0 12px; }
                    p { color: #c2c2d2; line-height: 1.7; margin: 0; }
                    .wait { background: #323255; border-radius: 16px; color: #e7e7f2; margin: 28px 0 20px; padding: 16px; }
                    strong { color: #ff9db0; font-size: 24px; }
                    a { background: #e94560; border-radius: 10px; color: #fff; display: inline-block; font-weight: 650; padding: 12px 22px; text-decoration: none; }
                    a:hover { background: #f15d76; }
                    small { color: #9c9cb4; display: block; margin-top: 22px; }
                  </style>
                </head>
                <body>
                  <main>
                    <div class="icon" aria-hidden="true">🌿</div>
                    <h1>慢一点，休息一下</h1>
                    <p>你刚刚操作得有些快。稍等片刻，就可以继续啦。</p>
                    <div class="wait">大约还有 <strong id="countdown">{{retryAfterSeconds}}</strong> 秒</div>
                    <a href="/">回到首页</a>
                    <small>不用着急，我们马上见。</small>
                  </main>
                  <script>
                    let remaining = {{retryAfterSeconds}};
                    const countdown = document.getElementById('countdown');
                    const timer = setInterval(() => {
                      remaining -= 1;
                      countdown.textContent = Math.max(0, remaining);
                      if (remaining <= 0) clearInterval(timer);
                    }, 1000);
                  </script>
                </body>
                </html>
                """.replace("{{retryAfterSeconds}}", Long.toString(retryAfterSeconds));
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
