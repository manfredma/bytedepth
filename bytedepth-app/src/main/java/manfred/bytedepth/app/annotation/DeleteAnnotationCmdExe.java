package manfred.bytedepth.app.annotation;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteAnnotationCmdExe {

    private final AnnotationRepositoryPort annotationRepository;
    private final manfred.bytedepth.app.post.PostWriteTransaction transactions;
    private final AnnotationRequestPort requests;

    /** 仅批注作者可删除。 */
    public void execute(Long annotationId, Long postId, Long userId, String ownerTokenHash) {
        transactions.withLockedPost(postId, post -> {
            var annotation = annotationRepository
                    .findById(annotationId)
                    .orElseThrow(() -> new DomainException("批注不存在：" + annotationId));
            if (!postId.equals(annotation.postId())) {
                throw new DomainException("批注不属于当前文章");
            }
            boolean ownedByUser = userId != null && userId.equals(annotation.userId());
            boolean ownedByVisitor = ownerTokenHash != null && ownerTokenHash.equals(annotation.ownerTokenHash());
            if (!ownedByUser && !ownedByVisitor) {
                throw new DomainException("只能删除自己的批注");
            }
            requests.markDeleted(annotationId);
            annotationRepository.delete(annotationId);
            return null;
        });
    }
}
