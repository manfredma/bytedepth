package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** 网页覆盖在 SessionRepository 保存前执行，原生移动链仍使用其既有 repository 默认值。 */
public final class WebSessionTimeoutFilter extends OncePerRequestFilter {
    private final int seconds;

    public WebSessionTimeoutFilter(int seconds) {
        this.seconds = seconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // sendRedirect 可在过滤链返回前提交 Session，因此创建/读取时即应用网页时长。
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public HttpSession getSession(boolean create) {
                        var session = super.getSession(create);
                        if (session != null) {
                            session.setMaxInactiveInterval(seconds);
                        }
                        return session;
                    }

                    @Override
                    public HttpSession getSession() {
                        return getSession(true);
                    }
                },
                response);
    }
}
