package manfred.bytedepth.adapter.web.portal;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;

class NetworkMapPropertiesTest {

    @Test
    void exposesConfiguredGroupsThroughJavaBeanAccessor() {
        var groups = List.of(new NetworkMapProperties.Group("resources", "资源", "说明", List.of(
                new NetworkMapProperties.Site("Example", URI.create("https://example.test"), "说明"))));
        var properties = new NetworkMapProperties(groups);

        assertThat(properties.getGroups()).isSameAs(groups);
    }

    @Test
    void rejectsAnHttpSiteUrl() {
        var properties = new NetworkMapProperties(List.of(
                new NetworkMapProperties.Group("resources", "资源", "说明", List.of(
                        new NetworkMapProperties.Site("HTTP", URI.create("http://example.test"), "说明")))));

        assertThat(validate(properties)).contains("url must use https");
    }

    @Test
    void rejectsMissingSiteUrl() {
        var site = new NetworkMapProperties.Site("Missing URL", null, "说明");
        var properties = new NetworkMapProperties(List.of(
                new NetworkMapProperties.Group("resources", "资源", "说明", List.of(site))));

        assertThat(site.hasHttpsUrl()).isFalse();
        assertThat(violationPaths(properties)).contains("groups[0].sites[0].url");
    }

    @Test
    void rejectsEmptyGroups() {
        assertThat(violationPaths(new NetworkMapProperties(List.of())))
                .containsExactly("groups");
    }

    @Test
    void rejectsGroupsWithoutSites() {
        var properties = new NetworkMapProperties(List.of(
                new NetworkMapProperties.Group("resources", "资源", "说明", List.of())));

        assertThat(violationPaths(properties)).containsExactly("groups[0].sites");
    }

    @Test
    void rejectsBlankRequiredFields() {
        var properties = new NetworkMapProperties(List.of(
                new NetworkMapProperties.Group("", "", "", List.of(
                        new NetworkMapProperties.Site("", URI.create("https://example.test"), "")))));

        assertThat(violationPaths(properties)).containsExactlyInAnyOrder(
                "groups[0].id", "groups[0].title", "groups[0].description",
                "groups[0].sites[0].name", "groups[0].sites[0].description");
    }

    @Test
    void preservesYamlGroupAndSiteOrder() throws Exception {
        var properties = bindYaml("network-map.yml");

        assertThat(properties.groups()).extracting(NetworkMapProperties.Group::id)
                .containsExactly("bytedepth", "developer-resources");
        assertThat(properties.groups().getFirst().sites()).extracting(NetworkMapProperties.Site::name)
                .containsExactly("ByteDepth", "Career", "Toolbox", "工作台");
    }

    private List<String> validate(NetworkMapProperties properties) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(properties).stream()
                    .map(violation -> violation.getMessage())
                    .toList();
        }
    }

    private List<String> violationPaths(NetworkMapProperties properties) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(properties).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .toList();
        }
    }

    private NetworkMapProperties bindYaml(String fileName) throws Exception {
        var yaml = Path.of("..", "bytedepth-start", "src", "main", "resources", fileName).toAbsolutePath().toUri();
        var environment = new StandardEnvironment();
        var propertySources = new YamlPropertySourceLoader().load(fileName, new org.springframework.core.io.UrlResource(yaml));
        propertySources.forEach(source -> environment.getPropertySources().addFirst(source));
        return Binder.get(environment).bind("bytedepth.network", Bindable.of(NetworkMapProperties.class))
                .orElseThrow(() -> new IllegalStateException("network map YAML did not bind"));
    }
}
