package manfred.bytedepth.infrastructure.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.domain.stats.PostViewedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步消费 {@link PostViewedEvent}，解析 IP 地理位置并写入访问日志。
 * 任何异常均打 ERROR 日志，不影响用户访问（异步线程隔离）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostViewEventHandler {

    private final GeoIpService geoIpService;
    private final PostViewLogMapper postViewLogMapper;

    @Async
    @EventListener
    public void onPostViewed(PostViewedEvent event) {
        try {
            GeoInfo geo = geoIpService.resolve(event.ip());
            PostViewLogDO log = new PostViewLogDO();
            log.setPostId(event.postId());
            log.setUserId(event.userId());
            log.setIp(event.ip());
            log.setUserAgent(event.userAgent());
            log.setReferer(event.referer());
            log.setCountry(geo.country());
            log.setCity(geo.city());
            log.setVisitedAt(event.occurredAt());
            postViewLogMapper.insert(log);
        } catch (Exception e) {
            log.error("访问日志写入失败 postId={} ip={}", event.postId(), event.ip(), e);
        }
    }
}
