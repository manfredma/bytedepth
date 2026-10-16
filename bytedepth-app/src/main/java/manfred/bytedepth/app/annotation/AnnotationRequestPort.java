package manfred.bytedepth.app.annotation;

import java.util.Optional;
import java.util.UUID;

public interface AnnotationRequestPort {
    record Scope(long postId, String ownerKey, UUID requestId) {}

    record Entry(Scope scope, String payloadHash, long annotationId, boolean deleted) {}

    Optional<Entry> find(Scope scope);

    void insert(Scope scope, String payloadHash, long annotationId);

    void markDeleted(long annotationId);
}
