package manfred.bytedepth.infrastructure.annotation;

import lombok.Data;

@Data
public class AnnotationRequestDO {
    private Long postId;
    private String ownerKey;
    private String requestId;
    private String payloadHash;
    private Long annotationId;
    private Boolean deleted;
}
