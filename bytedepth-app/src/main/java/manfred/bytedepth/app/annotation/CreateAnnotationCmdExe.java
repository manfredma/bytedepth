package manfred.bytedepth.app.annotation;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.annotation.PostAnnotation;
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

    public PostAnnotation execute(Long postId, Long userId, String selectedText,
                                  String annotationText, String color,
                                  int startOffset, int endOffset) {
        validate(postId, userId, selectedText, annotationText, color, startOffset, endOffset);
        return annotationRepository.save(new PostAnnotation(
                null, postId, userId, selectedText, annotationText, color,
                startOffset, endOffset, LocalDateTime.now()));
    }

    static void validate(Long postId, Long userId, String selectedText, String annotationText,
                         String color, int startOffset, int endOffset) {
        if (postId == null || userId == null) {
            throw new DomainException("文章与用户不能为空");
        }
        if (selectedText == null || selectedText.isBlank()
                || selectedText.length() > MAX_SELECTED_TEXT_LENGTH) {
            throw new DomainException("批注文本不能为空且不超过 " + MAX_SELECTED_TEXT_LENGTH + " 字");
        }
        if (annotationText == null || annotationText.isBlank()
                || annotationText.length() > MAX_ANNOTATION_TEXT_LENGTH) {
            throw new DomainException("批注内容不能为空且不超过 " + MAX_ANNOTATION_TEXT_LENGTH + " 字");
        }
        if (color == null || !AnnotationColor.isSupported(color)) {
            throw new DomainException("不支持的批注颜色：" + color);
        }
        if (startOffset < 0 || endOffset <= startOffset || endOffset > MAX_OFFSET) {
            throw new DomainException("批注偏移越界");
        }
    }
}
