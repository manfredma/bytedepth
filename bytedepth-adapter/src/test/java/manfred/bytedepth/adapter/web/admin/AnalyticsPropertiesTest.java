package manfred.bytedepth.adapter.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;

class AnalyticsPropertiesTest {

    @Test
    void bindsConfiguredLaunchDate() throws Exception {
        assertThat(bindApplicationYaml().launchDate())
                .isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void rejectsMissingLaunchDate() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new AnalyticsProperties(null));
            assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("launchDate");
        }
    }

    private AnalyticsProperties bindApplicationYaml() throws Exception {
        var yaml = Path.of("..", "bytedepth-start", "src", "main", "resources", "application.yml")
                .toAbsolutePath().toUri();
        var environment = new StandardEnvironment();
        var propertySources = new YamlPropertySourceLoader().load(
                "application.yml", new org.springframework.core.io.UrlResource(yaml));
        propertySources.forEach(source -> environment.getPropertySources().addFirst(source));
        return Binder.get(environment).bind("bytedepth.analytics", Bindable.of(AnalyticsProperties.class))
                .orElseThrow(() -> new IllegalStateException("analytics YAML did not bind"));
    }
}
