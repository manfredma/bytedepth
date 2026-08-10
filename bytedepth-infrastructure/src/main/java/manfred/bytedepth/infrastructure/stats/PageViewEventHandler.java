package manfred.bytedepth.infrastructure.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.domain.stats.PageViewedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步消费 {@link PageViewedEvent}，解析 IP 地理位置并写入页面访问日志。
 * 任何异常均打 ERROR 日志，不影响用户访问（异步线程隔离）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageViewEventHandler {

    private final GeoIpService geoIpService;
    private final PageViewLogMapper pageViewLogMapper;

    @Async
    @EventListener
    public void onPageViewed(PageViewedEvent event) {
        try {
            GeoInfo geo = geoIpService.resolve(event.ip());
            PageViewLogDO log = new PageViewLogDO();
            log.setPagePath(event.pagePath());
            log.setUserId(event.userId());
            log.setIp(event.ip());
            log.setUserAgent(event.userAgent());
            log.setReferer(event.referer());
            log.setCountry(geo.country());
            log.setCity(geo.city());
            log.setVisitedAt(event.occurredAt());
            pageViewLogMapper.insertLog(log);
        } catch (Exception e) {
            log.error("页面访问日志写入失败 pagePath={} ip={}", event.pagePath(), event.ip(), e);
        }
    }
}
