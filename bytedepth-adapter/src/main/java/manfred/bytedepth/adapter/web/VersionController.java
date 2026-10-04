package manfred.bytedepth.adapter.web;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {
  private final BuildProperties build;

  public VersionController(BuildProperties build) {
    this.build = build;
  }

  @GetMapping("/version")
  public VersionResponse version() {
    return new VersionResponse(
        value(build.getVersion()), value(build.get("commitId")), value(build.get("builtAt")));
  }

  private static String value(String value) {
    return value == null ? "unknown" : value;
  }

  public record VersionResponse(String version, String commitId, String builtAt) {}
}
