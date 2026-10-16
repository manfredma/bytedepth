package manfred.bytedepth.app.annotation;

import static manfred.bytedepth.app.annotation.AnnotationText.*;

import java.util.Objects;
import manfred.bytedepth.domain.annotation.AnnotationAnchor;
import manfred.bytedepth.domain.annotation.PlacementReason;
import manfred.bytedepth.domain.post.document.ArticleDocument;
import org.springframework.stereotype.Component;

@Component
public class AnchorValidator {
    public void validate(AnnotationAnchor a, ArticleDocument source) {
        if (a == null
                || source == null
                || source.text() == null
                || a.schemaVersion() != 1
                || a.sourceRevision() < 1
                || a.requestId() == null
                || source.projectionVersion() != 1
                || a.projectionVersion() != source.projectionVersion()
                || a.digest() == null
                || !a.digest().matches("[0-9a-f]{64}")
                || !Objects.equals(a.digest(), source.digest())
                || a.exact() == null
                || a.prefix() == null
                || a.suffix() == null
                || a.exact().codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF)
                || a.exact().codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))
                || length(a.exact()) > 500
                || a.start() < 0
                || a.end() <= a.start()
                || a.end() > length(source.text())) throw invalid(PlacementReason.MODEL_MISMATCH);
        if (!slice(source.text(), a.start(), a.end()).equals(a.exact())) throw invalid(PlacementReason.QUOTE_CHANGED);
        if (!slice(source.text(), Math.max(0, a.start() - 128), a.start()).equals(a.prefix())
                || !slice(source.text(), a.end(), Math.min(length(source.text()), a.end() + 128))
                        .equals(a.suffix())) throw invalid(PlacementReason.MODEL_MISMATCH);
        var expected = spans(source, a.start(), a.end());
        if (expected.isEmpty() || !expected.equals(a.spans())) throw invalid(PlacementReason.MODEL_MISMATCH);
        var first = source.segments().stream()
                .filter(s -> s.id().equals(expected.getFirst().segmentId()))
                .findFirst()
                .orElseThrow();
        var last = source.segments().stream()
                .filter(s -> s.id().equals(expected.getLast().segmentId()))
                .findFirst()
                .orElseThrow();
        if (first.start() + expected.getFirst().start() != a.start()
                || last.start() + expected.getLast().end() != a.end()) throw invalid(PlacementReason.MODEL_MISMATCH);
        if (source.segments().stream()
                .anyMatch(s -> s.kind().equals("mermaid") && s.start() < a.end() && s.end() > a.start()))
            throw invalid(PlacementReason.NO_VISIBLE_SOURCE);
    }

    private static InvalidAnchorException invalid(PlacementReason reason) {
        return new InvalidAnchorException(reason);
    }
}
