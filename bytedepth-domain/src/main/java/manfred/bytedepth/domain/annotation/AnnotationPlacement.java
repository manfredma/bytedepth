package manfred.bytedepth.domain.annotation;

import java.util.List;
import manfred.bytedepth.domain.post.document.SegmentSpan;

public record AnnotationPlacement(
        long annotationId,
        long targetRevision,
        int projectionVersion,
        int resolverVersion,
        PlacementStatus status,
        List<SegmentSpan> spans,
        PlacementReason reason) {
    public AnnotationPlacement {
        spans = List.copyOf(spans);
    }
}
