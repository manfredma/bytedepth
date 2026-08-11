package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;

import java.time.LocalDateTime;

/** 批注 API 传输对象。 */
public record PostAnnotationDTO(
        Long id,
        Long postId,
        String selectedText,
        String annotationText,
        String color,
        AnnotationVisibility visibility,
        int startOffset,
        int endOffset,
        LocalDateTime createdAt,
        boolean ownedByCurrentVisitor
) {
    public static PostAnnotationDTO from(PostAnnotation annotation, Long userId, String ownerTokenHash) {
        return new PostAnnotationDTO(
                annotation.id(), annotation.postId(),
                annotation.selectedText(), annotation.annotationText(), annotation.color(),
                annotation.visibility(), annotation.startOffset(), annotation.endOffset(), annotation.createdAt(),
                (userId != null && userId.equals(annotation.userId()))
                        || (ownerTokenHash != null && ownerTokenHash.equals(annotation.ownerTokenHash())));
    }
}
