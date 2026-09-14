package manfred.bytedepth.adapter.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@RestController
public class VersionController {
    @GetMapping("/version")
    public BuildMetadata version() {
        return load(getClass().getClassLoader().getResourceAsStream("bytedepth-build.properties"));
    }

    static BuildMetadata load(InputStream input) {
        Properties properties = new Properties();
        try (input) {
            if (input != null) properties.load(input);
        } catch (IOException ignored) {
            // Return unknown metadata rather than breaking health diagnostics.
        }
        return new BuildMetadata(properties.getProperty("version", "unknown"),
                properties.getProperty("commitId", "unknown"), properties.getProperty("builtAt", "unknown"));
    }

    public record BuildMetadata(String version, String commitId, String builtAt) {}
}
