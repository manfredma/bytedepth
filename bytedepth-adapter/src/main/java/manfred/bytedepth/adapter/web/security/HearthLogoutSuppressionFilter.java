package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 退出响应与在途认证响应交错时，晚到 Session 也不能重新提供权限；匿名交互登录材料仍保留。 */
public final class HearthLogoutSuppressionFilter extends OncePerRequestFilter {
    private final HearthSilentAuthentication silent = new HearthSilentAuthentication();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (silent.suppressed(request) && silent.authenticated()) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) session.invalidate();
        }
        chain.doFilter(request, response);
    }
}
