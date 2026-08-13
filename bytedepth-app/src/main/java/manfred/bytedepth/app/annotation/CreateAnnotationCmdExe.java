package manfred.bytedepth.app.annotation;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CreateAnnotationCmdExe {

    static final int MAX_SELECTED_TEXT_LENGTH = 500;
    static final int MAX_ANNOTATION_TEXT_LENGTH = 2000;
    static final int MAX_OFFSET = 1_000_000;

    private final AnnotationRepositoryPort annotationRepository;

    public PostAnnotation execute(Long postId, Long userId, String ownerTokenHash, String selectedText,
                                  String annotationText, String color, AnnotationVisibility visibility,
                                  int startOffset, int endOffset) {
        validate(postId, userId, ownerTokenHash, selectedText, annotationText, color, visibility, startOffset, endOffset);
        return annotationRepository.save(new PostAnnotation(
                null, postId, userId, ownerTokenHash, selectedText, normalizeAnnotationText(annotationText), color, visibility,
                startOffset, endOffset, LocalDateTime.now(), false));
    }

    static void validate(Long postId, Long userId, String ownerTokenHash, String selectedText, String annotationText,
                         String color, AnnotationVisibility visibility, int startOffset, int endOffset) {
        if (postId == null || (userId == null && (ownerTokenHash == null || ownerTokenHash.isBlank()))) {
            throw new DomainException("文章与批注身份不能为空");
        }
        if (selectedText == null || selectedText.isBlank()
                || selectedText.length() > MAX_SELECTED_TEXT_LENGTH) {
            throw new DomainException("批注文本不能为空且不超过 " + MAX_SELECTED_TEXT_LENGTH + " 字");
        }
        if (annotationText != null && annotationText.length() > MAX_ANNOTATION_TEXT_LENGTH) {
            throw new DomainException("批注内容不超过 " + MAX_ANNOTATION_TEXT_LENGTH + " 字");
        }
        if (color == null || !AnnotationColor.isSupported(color)) {
            throw new DomainException("不支持的批注颜色：" + color);
        }
        if (visibility == null) {
            throw new DomainException("批注可见范围不能为空");
        }
        if (startOffset < 0 || endOffset <= startOffset || endOffset > MAX_OFFSET) {
            throw new DomainException("批注偏移越界");
        }
    }

    private static String normalizeAnnotationText(String annotationText) {
        return annotationText == null || annotationText.isBlank() ? null : annotationText.trim();
    }
}
