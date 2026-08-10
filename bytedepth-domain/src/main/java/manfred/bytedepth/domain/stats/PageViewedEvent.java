package manfred.bytedepth.domain.stats;

import java.time.LocalDateTime;

/**
 * 公开页面被访问领域事件。
 * 由 {@code PageViewInterceptor} 对首页、关于页等非文章页面发布，
 * PageViewEventHandler 异步消费写入页面访问日志。
 */
public record PageViewedEvent(
        String pagePath,
        Long userId,        // 匿名访客为 null
        String ip,
        String userAgent,
        String referer,
        LocalDateTime occurredAt
) {}
