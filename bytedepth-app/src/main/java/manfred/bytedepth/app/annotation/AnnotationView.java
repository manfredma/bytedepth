package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.AnnotationPlacement;
import manfred.bytedepth.domain.annotation.PostAnnotation;

public record AnnotationView(PostAnnotation annotation, AnnotationPlacement placement, String targetDigest) {}
