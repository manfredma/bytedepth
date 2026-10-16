package manfred.bytedepth.app.annotation;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListAnnotationsQryExe {

    private final AnnotationRepositoryPort annotationRepository;
    private final manfred.bytedepth.app.post.document.ArticleRevisionService current;
    private final manfred.bytedepth.app.post.PostContentRevisionPort revisions;
    private final AnnotationPlacementPort placements;
    private final AnnotationResolver resolver;

    public AnnotationView viewCurrent(PostAnnotation annotation) {
        return view(annotation, current.current(annotation.postId()).revision());
    }

    public List<AnnotationView> views(Long postId, Long userId, String ownerTokenHash) {
        var target = current.current(postId).revision();
        return viewsFor(target, userId, ownerTokenHash);
    }

    public List<AnnotationView> viewsFor(
            manfred.bytedepth.domain.post.PostContentRevision target, Long userId, String ownerTokenHash) {
        return execute(target.postId(), userId, ownerTokenHash).stream()
                .map(a -> view(a, target))
                .toList();
    }

    public AnnotationView view(PostAnnotation annotation, manfred.bytedepth.domain.post.PostContentRevision target) {
        var placement = placements
                .find(
                        annotation.id(),
                        target.contentVersion(),
                        target.document().projectionVersion(),
                        1)
                .orElseGet(() -> {
                    manfred.bytedepth.domain.annotation.AnnotationPlacement resolved;
                    if (annotation.anchor() == null) {
                        resolved = new manfred.bytedepth.domain.annotation.AnnotationPlacement(
                                annotation.id(),
                                target.contentVersion(),
                                target.document().projectionVersion(),
                                1,
                                manfred.bytedepth.domain.annotation.PlacementStatus.LEGACY_UNVERIFIED,
                                List.of(),
                                manfred.bytedepth.domain.annotation.PlacementReason.LEGACY_EVIDENCE_MISSING);
                    } else {
                        var source = revisions.find(
                                annotation.postId(), annotation.anchor().sourceRevision());
                        resolved = resolver.resolve(
                                annotation.id(),
                                annotation.anchor(),
                                source.map(manfred.bytedepth.domain.post.PostContentRevision::document)
                                        .orElse(null),
                                target.contentVersion(),
                                target.document());
                    }
                    placements.save(resolved);
                    return resolved;
                });
        return new AnnotationView(annotation, placement, target.document().digest());
    }

    public List<PostAnnotation> execute(Long postId, Long userId, String ownerTokenHash) {
        return annotationRepository.findVisibleByPostId(postId, userId, ownerTokenHash).stream()
                .sorted(Comparator.comparingInt(PostAnnotation::startOffset))
                .toList();
    }
}
