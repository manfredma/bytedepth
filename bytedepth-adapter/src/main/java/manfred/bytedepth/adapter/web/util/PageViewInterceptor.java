package manfred.bytedepth.adapter.web.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import manfred.bytedepth.domain.stats.PageViewedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/**
 * 拦截公开页面 GET 请求，发布 {@link PageViewedEvent} 供统计。
 * <p>
 * 白名单路径在 {@code WebMvcConfig.addInterceptors()} 中配置；
 * 仅对 {@link HandlerMethod} 类型的 handler 生效（静态资源自动跳过）。
 * 不作为 {@code @Component} 声明，由 {@code WebMvcConfig} 以 {@code @Bean} 创建，
 * 避免被 {@code @WebMvcTest} 切片测试意外加载。
 */
public class PageViewInterceptor implements HandlerInterceptor {

    private final VisitRequestFilter visitRequestFilter;
    private final ApplicationEventPublisher eventPublisher;

    public PageViewInterceptor(VisitRequestFilter visitRequestFilter,
                               ApplicationEventPublisher eventPublisher) {
        this.visitRequestFilter = visitRequestFilter;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String userAgent = WebUtils.truncate(request.getHeader("User-Agent"), 512);
        if (!visitRequestFilter.shouldRecord(new VisitRequestFilter.Request(userAgent))) {
            return true;
        }
        eventPublisher.publishEvent(new PageViewedEvent(
                request.getRequestURI(),
                null,  // 页面访问统一匿名
                WebUtils.getClientIp(request),
                userAgent,
                WebUtils.truncate(request.getHeader("Referer"), 512),
                LocalDateTime.now()
        ));
        return true;
    }
}