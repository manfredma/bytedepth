package manfred.bytedepth.infrastructure.annotation;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.annotation.AnnotationRequestPort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisAnnotationRequestRepository implements AnnotationRequestPort {
    private final AnnotationRequestMapper mapper;

    @Override
    public Optional<Entry> find(Scope scope) {
        return Optional.ofNullable(mapper.find(
                        scope.postId(), scope.ownerKey(), scope.requestId().toString()))
                .map(row -> new Entry(
                        scope, row.getPayloadHash(), row.getAnnotationId(), Boolean.TRUE.equals(row.getDeleted())));
    }

    @Override
    public void insert(Scope scope, String payloadHash, long annotationId) {
        var row = new AnnotationRequestDO();
        row.setPostId(scope.postId());
        row.setOwnerKey(scope.ownerKey());
        row.setRequestId(scope.requestId().toString());
        row.setPayloadHash(payloadHash);
        row.setAnnotationId(annotationId);
        mapper.insert(row);
    }

    @Override
    public void markDeleted(long annotationId) {
        mapper.markDeleted(annotationId);
    }
}
