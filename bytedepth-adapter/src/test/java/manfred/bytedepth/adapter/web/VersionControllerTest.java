package manfred.bytedepth.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class VersionControllerTest {
    @Test
    void returnsBuildMetadataFromSpringBootBuildInfo() {
        Properties properties = new Properties();
        properties.setProperty("version", "2.27.0-SNAPSHOT");
        properties.setProperty("applicationVersion", "2.27.0");
        properties.setProperty("commitId", "a".repeat(40));
        properties.setProperty("builtAt", "2026-10-01T00:00:00Z");
        VersionController.VersionResponse metadata = new VersionController(new BuildProperties(properties)).version();
        assertThat(metadata.version()).isEqualTo("2.27.0");
        assertThat(metadata.buildVersion()).isEqualTo("2.27.0-SNAPSHOT");
        assertThat(metadata.commitId()).isEqualTo("a".repeat(40));
        assertThat(metadata.builtAt()).isEqualTo("2026-10-01T00:00:00Z");
    }

    @Test
    void recordExposesAllFields() {
        VersionController.VersionResponse metadata =
                new VersionController.VersionResponse("1", "1-SNAPSHOT", "commit", "time");
        assertThat(metadata.version()).isEqualTo("1");
        assertThat(metadata.buildVersion()).isEqualTo("1-SNAPSHOT");
        assertThat(metadata.commitId()).isEqualTo("commit");
        assertThat(metadata.builtAt()).isEqualTo("time");
    }

    @Test
    void preservesUnknownFallbackForMissingBuildFields() {
        Properties properties = new Properties();
        VersionController.VersionResponse metadata = new VersionController(new BuildProperties(properties)).version();
        assertThat(metadata.version()).isEqualTo("unknown");
        assertThat(metadata.buildVersion()).isEqualTo("unknown");
        assertThat(metadata.commitId()).isEqualTo("unknown");
        assertThat(metadata.builtAt()).isEqualTo("unknown");
    }
}
