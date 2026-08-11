package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.PostAnnotation;

import java.time.LocalDateTime;

/** 批注 API 传输对象。 */
public record PostAnnotationDTO(
        Long id,
        Long postId,
        Long userId,
        String selectedText,
        String annotationText,
        String color,
        int startOffset,
        int endOffset,
        LocalDateTime createdAt
) {
    public static PostAnnotationDTO from(PostAnnotation annotation) {
        return new PostAnnotationDTO(
                annotation.id(), annotation.postId(), annotation.userId(),
                annotation.selectedText(), annotation.annotationText(), annotation.color(),
                annotation.startOffset(), annotation.endOffset(), annotation.createdAt());
    }
}
