package manfred.bytedepth.adapter.web.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "bytedepth.network")
@Validated
public record NetworkMapProperties(@NotEmpty List<@Valid Group> groups) {

    public List<Group> getGroups() {
        return groups;
    }

    public record Group(@NotBlank String id, @NotBlank String title,
                        @NotBlank String description, @NotEmpty List<@Valid Site> sites) {
    }

    public record Site(@NotBlank String name, @NotNull URI url, @NotBlank String description) {

        @AssertTrue(message = "url must use https")
        public boolean hasHttpsUrl() {
            return url != null && "https".equalsIgnoreCase(url.getScheme());
        }
    }
}
