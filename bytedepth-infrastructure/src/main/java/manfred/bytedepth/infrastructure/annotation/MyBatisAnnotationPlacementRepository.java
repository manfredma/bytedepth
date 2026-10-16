package manfred.bytedepth.infrastructure.annotation;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.annotation.AnnotationPlacementPort;
import manfred.bytedepth.domain.annotation.AnnotationPlacement;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisAnnotationPlacementRepository implements AnnotationPlacementPort {
    private final AnnotationPlacementMapper mapper;

    @Override
    public Optional<AnnotationPlacement> find(
            long annotationId, long targetRevision, int projectionVersion, int resolverVersion) {
        return Optional.ofNullable(mapper.find(annotationId, targetRevision, projectionVersion, resolverVersion))
                .map(row -> AnnotationJson.read(row.getPlacementJson(), AnnotationPlacement.class));
    }

    @Override
    public void save(AnnotationPlacement placement) {
        var row = new AnnotationPlacementDO();
        row.setAnnotationId(placement.annotationId());
        row.setTargetRevision(placement.targetRevision());
        row.setProjectionVersion(placement.projectionVersion());
        row.setResolverVersion(placement.resolverVersion());
        row.setPlacementJson(AnnotationJson.write(placement));
        mapper.save(row);
    }
}
