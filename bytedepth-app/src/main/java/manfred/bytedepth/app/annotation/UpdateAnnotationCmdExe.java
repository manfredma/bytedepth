package manfred.bytedepth.app.annotation;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.stereotype.Component;

/** 编辑自己已创建的批注文字与可见范围。 */
@Component
@RequiredArgsConstructor
public class UpdateAnnotationCmdExe {

    private final AnnotationRepositoryPort annotationRepository;

    public PostAnnotation execute(Long annotationId, Long postId, Long userId, String ownerTokenHash,
                                  String annotationText, AnnotationVisibility visibility) {
        var annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> new DomainException("批注不存在：" + annotationId));
        if (!postId.equals(annotation.postId())) {
            throw new DomainException("批注不属于当前文章");
        }
        boolean ownedByUser = userId != null && userId.equals(annotation.userId());
        boolean ownedByVisitor = ownerTokenHash != null && ownerTokenHash.equals(annotation.ownerTokenHash());
        if (!ownedByUser && !ownedByVisitor) {
            throw new DomainException("只能编辑自己的批注");
        }
        if (annotationText != null && annotationText.length() > CreateAnnotationCmdExe.MAX_ANNOTATION_TEXT_LENGTH) {
            throw new DomainException("批注内容不超过 " + CreateAnnotationCmdExe.MAX_ANNOTATION_TEXT_LENGTH + " 字");
        }
        if (visibility == null) {
            throw new DomainException("批注可见范围不能为空");
        }
        String normalized = annotationText == null || annotationText.isBlank() ? null : annotationText.trim();
        return annotationRepository.update(new PostAnnotation(annotation.id(), annotation.postId(), annotation.userId(),
                annotation.ownerTokenHash(), annotation.selectedText(), normalized, annotation.color(), visibility,
                annotation.startOffset(), annotation.endOffset(), annotation.createdAt(), annotation.deleted()));
    }
}
