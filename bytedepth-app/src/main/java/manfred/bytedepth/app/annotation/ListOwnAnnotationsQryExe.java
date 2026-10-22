package manfred.bytedepth.app.annotation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.document.ArticleRevisionService;
import manfred.bytedepth.app.post.query.PublicPostAccess;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListOwnAnnotationsQryExe {
    private final AnnotationRepositoryPort annotations;
    private final PublicPostAccess access;
    private final ArticleRevisionService current;
    private final ListAnnotationsQryExe query;

    public PageResult execute(Long userId, int page, int pageSize) {
        if (userId == null) throw new AnnotationOwnershipException();
        manfred.bytedepth.app.interaction.MobileInteractionService.pagination(page, pageSize);
        var items = annotations.findOwnPublished(userId, page, pageSize).stream()
                .map(annotation -> access.withPublishedPost(annotation.postId(), post -> {
                    var target = current.ensureCurrent(post).revision();
                    // ensureCurrent may migrate a legacy row; placement must use the resulting anchor.
                    var refreshed =
                            annotations.findById(annotation.id()).orElseThrow(AnnotationOwnershipException::new);
                    return query.view(refreshed, target);
                }))
                .toList();
        return new PageResult(items, annotations.countOwnPublished(userId));
    }

    public record PageResult(List<AnnotationView> items, long total) {}
}
