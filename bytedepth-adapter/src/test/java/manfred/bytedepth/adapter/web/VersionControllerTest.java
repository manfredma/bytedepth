package manfred.bytedepth.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class VersionControllerTest {
    @Test
    void returnsBuildMetadataFromSpringBootBuildInfo() {
        Properties properties = new Properties();
        properties.setProperty("version", "2.26.6-SNAPSHOT");
        properties.setProperty("commitId", "a".repeat(40));
        properties.setProperty("builtAt", "2026-10-01T00:00:00Z");
        VersionController.VersionResponse metadata = new VersionController(new BuildProperties(properties)).version();
        assertThat(metadata.version()).isEqualTo("2.26.6-SNAPSHOT");
        assertThat(metadata.commitId()).isEqualTo("a".repeat(40));
        assertThat(metadata.builtAt()).isEqualTo("2026-10-01T00:00:00Z");
    }

    @Test
    void recordExposesAllFields() {
        VersionController.VersionResponse metadata = new VersionController.VersionResponse("1", "commit", "time");
        assertThat(metadata.version()).isEqualTo("1");
        assertThat(metadata.commitId()).isEqualTo("commit");
        assertThat(metadata.builtAt()).isEqualTo("time");
    }

    @Test
    void preservesUnknownFallbackForMissingBuildFields() {
        Properties properties = new Properties();
        VersionController.VersionResponse metadata = new VersionController(new BuildProperties(properties)).version();
        assertThat(metadata.version()).isEqualTo("unknown");
        assertThat(metadata.commitId()).isEqualTo("unknown");
        assertThat(metadata.builtAt()).isEqualTo("unknown");
    }
}
