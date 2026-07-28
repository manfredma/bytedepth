package manfred.bytedepth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PostRatingFragmentTest {

    @Test
    void rendersAverageOnlyWhenRatingsExist() throws IOException {
        String template = classpathText("/templates/fragments/post-rating.html");

        assertThat(template)
                .contains("class=\"post-rating-average\"")
                .contains("${rating.ratingCount > 0}")
                .contains("人打了分，平均分：")
                .contains("formatDecimal(rating.averageRating, 1, 2)")
                .doesNotContain("成为第一个评分的人");
    }

    @Test
    void usesRoundedSvgStarsForSelectedAndUnselectedStates() throws IOException {
        String template = classpathText("/templates/fragments/post-rating.html");

        assertThat(template)
                .contains("class=\"post-rating-star-icon\"")
                .contains("viewBox=\"0 0 24 24\"")
                .contains("is-selected")
                .doesNotContain("? '★' : '☆'");
    }

    @Test
    void previewsTheCumulativeScoreWhenHoveringAStar() throws IOException {
        String css = classpathText("/static/css/post-rating.css");

        assertThat(css)
                .contains(".post-rating-star:hover ~ .post-rating-star.is-selected")
                .contains(".post-rating-star:has(~ .post-rating-star:hover)");
    }

    @Test
    void rendersInlineAtTheEndOfTheArticleInsteadOfFloating() throws IOException {
        String css = classpathText("/static/css/post-rating.css");

        assertThat(css)
                .contains("position: static")
                .contains("display: inline-flex")
                .doesNotContain("position: fixed")
                .doesNotContain("bottom: calc(76px");
    }

    private String classpathText(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("classpath resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
