package manfred.bytedepth.app.annotation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import manfred.bytedepth.app.post.MarkdownTextExtractor;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.post.PostContentRevision;
import org.springframework.stereotype.Component;

@Component
public class LegacyAnchorMigrator {
    private final AnchorValidator validator = new AnchorValidator();

    public PostAnnotation migrate(PostAnnotation legacy, PostContentRevision baseline) {
        if (legacy.anchor() != null
                || legacy.migrationBaselineRevision() != null
                || baseline == null
                || !java.util.Objects.equals(legacy.postId(), baseline.postId())
                || baseline.contentVersion() < 1
                || baseline.document() == null) return legacy;
        AnnotationAnchor anchor = null;
        String quote = legacy.selectedText();
        String oldText = MarkdownTextExtractor.renderedText(baseline.markdown());
        if (!legacy.deleted()
                && quote != null
                && !quote.isEmpty()
                && legacy.startOffset() >= 0
                && legacy.endOffset() > legacy.startOffset()
                && legacy.endOffset() <= oldText.length()
                && quote.equals(oldText.substring(legacy.startOffset(), legacy.endOffset()))) {
            var doc = baseline.document();
            int first = doc.text().indexOf(quote);
            if (first >= 0 && doc.text().indexOf(quote, first + 1) < 0) {
                int start = doc.text().codePointCount(0, first), end = start + AnnotationText.length(quote);
                var candidate = new AnnotationAnchor(
                        1,
                        baseline.contentVersion(),
                        doc.projectionVersion(),
                        doc.digest(),
                        quote,
                        AnnotationText.slice(doc.text(), Math.max(0, start - 128), start),
                        AnnotationText.slice(doc.text(), end, Math.min(AnnotationText.length(doc.text()), end + 128)),
                        start,
                        end,
                        AnnotationText.spans(doc, start, end),
                        UUID.nameUUIDFromBytes(
                                ("legacy:" + legacy.postId() + ":" + legacy.id()).getBytes(StandardCharsets.UTF_8)));
                try {
                    validator.validate(candidate, doc);
                    anchor = candidate;
                } catch (InvalidAnchorException invalid) {
                    anchor = null;
                }
            }
        }
        return new PostAnnotation(
                legacy.id(),
                legacy.postId(),
                legacy.userId(),
                legacy.ownerTokenHash(),
                legacy.selectedText(),
                legacy.annotationText(),
                legacy.color(),
                legacy.visibility(),
                legacy.startOffset(),
                legacy.endOffset(),
                legacy.createdAt(),
                legacy.deleted(),
                anchor,
                baseline.contentVersion());
    }
}
