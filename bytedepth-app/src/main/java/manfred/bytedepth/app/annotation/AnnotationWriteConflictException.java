package manfred.bytedepth.app.annotation;

public final class AnnotationWriteConflictException extends IllegalStateException {
    public enum Kind {
        REQUEST_REUSED,
        DELETED_REQUEST,
        STALE_REVISION
    }

    private final Kind kind;

    public AnnotationWriteConflictException(Kind kind) {
        super("Annotation write conflict: " + kind);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
