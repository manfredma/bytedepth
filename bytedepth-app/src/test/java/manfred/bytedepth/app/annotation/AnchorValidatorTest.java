package manfred.bytedepth.app.annotation;

import static manfred.bytedepth.app.annotation.AnnotationAnchorFixtures.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import manfred.bytedepth.app.post.document.ArticleDocumentService;
import manfred.bytedepth.domain.annotation.AnnotationAnchor;
import manfred.bytedepth.domain.post.document.SegmentSpan;
import org.junit.jupiter.api.Test;

class AnchorValidatorTest {
    private final ArticleDocumentService documents = new ArticleDocumentService();
    private final AnchorValidator validator = new AnchorValidator();

    @Test
    void U09_U34_invalidQuoteContextDigestAndSpansAreRejected() {
        var source = documents.render("前😀目标后").document();
        var a = anchor(source, "目标");
        var invalid = List.of(
                new AnnotationAnchor(
                        2,
                        7,
                        1,
                        a.digest(),
                        a.exact(),
                        a.prefix(),
                        a.suffix(),
                        a.start(),
                        a.end(),
                        a.spans(),
                        a.requestId()),
                new AnnotationAnchor(
                        1,
                        0,
                        1,
                        a.digest(),
                        a.exact(),
                        a.prefix(),
                        a.suffix(),
                        a.start(),
                        a.end(),
                        a.spans(),
                        a.requestId()),
                new AnnotationAnchor(
                        1,
                        7,
                        2,
                        a.digest(),
                        a.exact(),
                        a.prefix(),
                        a.suffix(),
                        a.start(),
                        a.end(),
                        a.spans(),
                        a.requestId()),
                new AnnotationAnchor(
                        1,
                        7,
                        1,
                        "wrong",
                        a.exact(),
                        a.prefix(),
                        a.suffix(),
                        a.start(),
                        a.end(),
                        a.spans(),
                        a.requestId()),
                new AnnotationAnchor(
                        1,
                        7,
                        1,
                        a.digest(),
                        "伪造",
                        a.prefix(),
                        a.suffix(),
                        a.start(),
                        a.end(),
                        a.spans(),
                        a.requestId()),
                new AnnotationAnchor(
                        1, 7, 1, a.digest(), a.exact(), "错", a.suffix(), a.start(), a.end(), a.spans(), a.requestId()),
                new AnnotationAnchor(
                        1, 7, 1, a.digest(), a.exact(), a.prefix(), "错", a.start(), a.end(), a.spans(), a.requestId()),
                new AnnotationAnchor(
                        1,
                        7,
                        1,
                        a.digest(),
                        a.exact(),
                        a.prefix(),
                        a.suffix(),
                        a.start(),
                        a.end(),
                        List.of(new SegmentSpan("missing", 0, 2)),
                        a.requestId()),
                new AnnotationAnchor(
                        1, 7, 1, a.digest(), a.exact(), a.prefix(), a.suffix(), 3, 2, a.spans(), a.requestId()),
                new AnnotationAnchor(
                        1, 7, 1, a.digest(), a.exact(), a.prefix(), a.suffix(), a.start(), a.end(), a.spans(), null));
        assertThatCode(() -> validator.validate(a, source)).doesNotThrowAnyException();
        for (var bad : invalid)
            assertThatThrownBy(() -> validator.validate(bad, source)).isInstanceOf(InvalidAnchorException.class);
    }

    @Test
    void U24_U25_U26_128CodepointContextPreservesSurrogatesCombiningMarksAndSpaces() {
        var source = documents
                .render("😀".repeat(130) + "目标" + "e\u0301".repeat(130))
                .document();
        var a = anchor(source, "目标");
        assertThat(a.prefix()).isEqualTo("😀".repeat(128));
        assertThat(a.suffix()).isEqualTo("e\u0301".repeat(64));
        assertThatCode(() -> validator.validate(a, source)).doesNotThrowAnyException();
        var whitespace = documents.render("前  &nbsp; 后").document();
        assertThatThrownBy(() -> validator.validate(anchor(whitespace, "  \u00a0 "), whitespace))
                .isInstanceOf(InvalidAnchorException.class);
    }

    @Test
    void U17_generatedMermaidTextCannotBeCreatedAsATextAnchor() {
        var source = documents.render("```mermaid\ngraph TD; A-->B\n```").document();
        assertThatThrownBy(() -> validator.validate(anchor(source, "graph"), source))
                .isInstanceOf(InvalidAnchorException.class);
    }

    @Test
    void U09_U12_U34_missingMalformedAndVirtualSeparatorBoundsAreRejected() {
        var source = documents.render("前😀目标后").document();
        var a = anchor(source, "目标");
        var fields = java.util.Arrays.asList(
                new Object[] {"digest", null},
                new Object[] {"exact", null},
                new Object[] {"prefix", null},
                new Object[] {"suffix", null},
                new Object[] {"exact", ""},
                new Object[] {"exact", "\uD800"},
                new Object[] {"start", -1},
                new Object[] {"end", 100},
                new Object[] {"spans", List.of()},
                new Object[] {"prefix", "😀".repeat(129)});
        for (var field : fields)
            assertThatThrownBy(() -> validator.validate(with(a, (String) field[0], field[1]), source))
                    .isInstanceOf(InvalidAnchorException.class);
        assertThatThrownBy(() -> validator.validate(null, source)).isInstanceOf(InvalidAnchorException.class);
        assertThatThrownBy(() -> validator.validate(a, null)).isInstanceOf(InvalidAnchorException.class);
        assertThatThrownBy(() -> validator.validate(
                        a, new manfred.bytedepth.domain.post.document.ArticleDocument(1, a.digest(), null, List.of())))
                .isInstanceOf(InvalidAnchorException.class);
        assertThatThrownBy(() -> validator.validate(
                        a,
                        new manfred.bytedepth.domain.post.document.ArticleDocument(
                                2, a.digest(), source.text(), source.segments())))
                .isInstanceOf(InvalidAnchorException.class);
        var longDoc = documents.render("x".repeat(501)).document();
        assertThatThrownBy(() -> validator.validate(anchor(longDoc, "x".repeat(501)), longDoc))
                .isInstanceOf(InvalidAnchorException.class);
        var separated = documents.render("前\n\n后").document();
        assertThatThrownBy(() -> validator.validate(anchorAt(separated, 1, 2), separated))
                .isInstanceOf(InvalidAnchorException.class);
        assertThatThrownBy(() -> validator.validate(anchorAt(separated, 0, 2), separated))
                .isInstanceOf(InvalidAnchorException.class);
    }

    @Test
    void U09_U17_U24_corruptedSnapshotAndEmojiAndMermaidNeighborBoundaries() {
        var source = documents.render("前😀后").document();
        var a = anchor(source, "😀");
        assertThatCode(() -> validator.validate(a, source)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(with(a, "digest", "0".repeat(64)), source))
                .isInstanceOf(InvalidAnchorException.class);
        var missingSegments = new manfred.bytedepth.domain.post.document.ArticleDocument(
                1, source.digest(), source.text(), List.of());
        assertThatThrownBy(() -> validator.validate(a, missingSegments)).isInstanceOf(InvalidAnchorException.class);
        var mermaid =
                documents.render("前\n\n```mermaid\ngraph TD; A-->B\n```\n\n后").document();
        assertThatCode(() -> validator.validate(anchor(mermaid, "前"), mermaid)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(anchor(mermaid, "后"), mermaid)).doesNotThrowAnyException();
        var boundary = documents.render("x".repeat(500)).document();
        assertThatCode(() -> validator.validate(anchor(boundary, "x".repeat(500)), boundary))
                .doesNotThrowAnyException();
    }
}
