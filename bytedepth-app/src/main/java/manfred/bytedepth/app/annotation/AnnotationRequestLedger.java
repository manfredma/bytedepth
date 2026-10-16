package manfred.bytedepth.app.annotation;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnnotationRequestLedger {
    private final AnnotationRequestPort requests;

    public Optional<AnnotationRequestPort.Entry> lookup(AnnotationRequestPort.Scope scope, String payloadHash) {
        var entry = requests.find(scope);
        if (entry.isPresent()) {
            if (!entry.get().payloadHash().equals(payloadHash))
                throw new AnnotationWriteConflictException(AnnotationWriteConflictException.Kind.REQUEST_REUSED);
            if (entry.get().deleted())
                throw new AnnotationWriteConflictException(AnnotationWriteConflictException.Kind.DELETED_REQUEST);
        }
        return entry;
    }
}
