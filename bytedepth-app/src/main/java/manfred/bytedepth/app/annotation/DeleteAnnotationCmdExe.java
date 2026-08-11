package manfred.bytedepth.app.annotation;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteAnnotationCmdExe {

    private final AnnotationRepositoryPort annotationRepository;

    /** 仅批注作者可删除。 */
    public void execute(Long annotationId, Long userId) {
        var annotation = annotationRepository.findById(annotationId)
                .orElseThrow(() -> new DomainException("批注不存在：" + annotationId));
        if (!annotation.userId().equals(userId)) {
            throw new DomainException("只能删除自己的批注");
        }
        annotationRepository.delete(annotationId);
    }
}
