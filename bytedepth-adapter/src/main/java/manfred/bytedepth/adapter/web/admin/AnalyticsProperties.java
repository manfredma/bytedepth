package manfred.bytedepth.adapter.web.admin;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;

/** 访问统计使用的站点级配置。 */
@ConfigurationProperties(prefix = "bytedepth.analytics")
@Validated
public record AnalyticsProperties(@NotNull LocalDate launchDate) {
}
