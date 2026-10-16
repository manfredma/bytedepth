package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.AnnotationAnchor;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;

public record CreateAnnotationRequest(
        AnnotationAnchor anchor, String annotationText, String color, AnnotationVisibility visibility) {}
