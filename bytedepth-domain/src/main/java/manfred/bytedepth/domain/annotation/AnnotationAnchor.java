package manfred.bytedepth.domain.annotation;

import java.util.List;
import java.util.UUID;
import manfred.bytedepth.domain.post.document.SegmentSpan;

public record AnnotationAnchor(
        int schemaVersion,
        long sourceRevision,
        int projectionVersion,
        String digest,
        String exact,
        String prefix,
        String suffix,
        int start,
        int end,
        List<SegmentSpan> spans,
        UUID requestId) {
    public AnnotationAnchor {
        spans = List.copyOf(spans);
    }
}
