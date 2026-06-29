package manfred.bytedepth.domain.stats;

import java.time.LocalDateTime;

/**
 * 文章被访问领域事件。
 * 由 PostController.detail() 发布，PostViewEventHandler 异步消费写入访问日志。
 */
public record PostViewedEvent(
        Long postId,
        Long userId,        // 匿名访客为 null
        String ip,
        String userAgent,
        String referer,
        LocalDateTime occurredAt
) {}
