package manfred.bytedepth.adapter.web;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class VersionControllerTest {
    @Test
    void returnsBuildMetadataFromClasspath() {
        VersionController.BuildMetadata metadata = new VersionController().version();
        assertThat(metadata.version()).isNotBlank();
        assertThat(metadata.commitId()).isNotBlank();
        assertThat(metadata.builtAt()).isNotBlank();
    }

    @Test
    void recordExposesAllFields() {
        VersionController.BuildMetadata metadata =
                new VersionController.BuildMetadata("1", "commit", "time");
        assertThat(metadata.version()).isEqualTo("1");
        assertThat(metadata.commitId()).isEqualTo("commit");
        assertThat(metadata.builtAt()).isEqualTo("time");
    }

    @Test
    void handlesMissingAndUnreadableMetadata() {
        assertThat(VersionController.load(null).version()).isEqualTo("unknown");
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("broken"); }
        };
        assertThat(VersionController.load(broken).version()).isEqualTo("unknown");
    }
}
