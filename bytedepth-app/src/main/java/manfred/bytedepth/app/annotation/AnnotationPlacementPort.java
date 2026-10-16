package manfred.bytedepth.app.annotation;

import java.util.Optional;
import manfred.bytedepth.domain.annotation.AnnotationPlacement;

public interface AnnotationPlacementPort {
    Optional<AnnotationPlacement> find(
            long annotationId, long targetRevision, int projectionVersion, int resolverVersion);

    void save(AnnotationPlacement placement);
}
