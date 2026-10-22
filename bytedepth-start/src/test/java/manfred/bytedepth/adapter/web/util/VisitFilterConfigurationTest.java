package manfred.bytedepth.adapter.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

class VisitFilterConfigurationTest {
    @Test
    void shippedConfigurationExcludesToolUasAndPreservesOrdinaryBrowsers() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var values = new HashMap<String, Object>();
        yaml.getObject().forEach((key, value) -> values.put(key.toString(), value));
        var properties = new Binder(new MapConfigurationPropertySource(values))
                .bind("bytedepth.visit-filter", VisitFilterProperties.class)
                .get();
        properties.validate();
        var filter = new VisitRequestFilter(properties);
        for (String ua : java.util.List.of("Codex/1.0", "Mozilla/5.0 Obsidian/1.0", "Python-urllib/3.12", "curl/8")) {
            assertThat(filter.shouldRecord(new VisitRequestFilter.Request(ua)))
                    .as(ua)
                    .isFalse();
        }
        assertThat(filter.shouldRecord(new VisitRequestFilter.Request("Mozilla/5.0 Chrome/130")))
                .isTrue();
        assertThat(filter.shouldRecord(new VisitRequestFilter.Request(null))).isTrue();
    }
}
