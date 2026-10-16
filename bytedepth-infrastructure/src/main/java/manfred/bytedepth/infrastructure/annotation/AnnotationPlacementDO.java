package manfred.bytedepth.infrastructure.annotation;

import lombok.Data;

@Data
public class AnnotationPlacementDO {
    private Long annotationId;
    private Long targetRevision;
    private Integer projectionVersion;
    private Integer resolverVersion;
    private String placementJson;
}
