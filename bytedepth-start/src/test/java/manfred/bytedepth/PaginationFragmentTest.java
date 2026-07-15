package manfred.bytedepth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationFragmentTest {

    @Test
    void usesAnIsolatedCompactComponentAndCollapsesPageJump() throws IOException {
        String template = classpathText("/templates/fragments/pagination.html");

        assertThat(template)
                .contains("bd-pagination-wrap")
                .contains("bd-pagination-nav")
                .contains("<details class=\"bd-pagination-jump\"")
                .contains(".bd-pagination-submit { white-space: nowrap; flex: 0 0 auto; }")
                .doesNotContain("class=\"pagination\"")
                .doesNotContain("class=\"page-btn");
    }

    private String classpathText(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("classpath resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
