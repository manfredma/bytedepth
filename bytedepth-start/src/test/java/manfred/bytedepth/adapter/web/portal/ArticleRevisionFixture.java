package manfred.bytedepth.adapter.web.portal;

import java.util.List;
import manfred.bytedepth.app.annotation.AnnotationView;
import manfred.bytedepth.app.post.document.ArticleDocumentService;
import manfred.bytedepth.app.post.document.ArticleRevisionService.RevisionedArticle;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.post.*;

final class ArticleRevisionFixture {
    static RevisionedArticle from(PostDTO dto) {
        var post = Post.reconstruct(
                dto.getId(),
                dto.getSlug(),
                dto.getTitle(),
                dto.getContent(),
                PostStatus.valueOf(dto.getStatus()),
                dto.getCreatedAt(),
                dto.getPublishedAt(),
                dto.getUpdatedAt(),
                dto.getCategoryId(),
                dto.getAuthorId(),
                dto.getFeatured(),
                dto.getContentVersion());
        var rendered = new ArticleDocumentService().render(post.getContent());
        return new RevisionedArticle(
                post,
                new PostContentRevision(
                        post.getId(), post.getContentVersion(), post.getContent(), rendered.document(), true),
                rendered);
    }

    static AnnotationView view(PostAnnotation annotation) {
        return new AnnotationView(
                annotation,
                new AnnotationPlacement(
                        annotation.id(),
                        1,
                        1,
                        1,
                        PlacementStatus.LEGACY_UNVERIFIED,
                        List.of(),
                        PlacementReason.LEGACY_EVIDENCE_MISSING),
                "d".repeat(64));
    }

    static List<AnnotationView> views(List<PostAnnotation> annotations) {
        return annotations.stream().map(ArticleRevisionFixture::view).toList();
    }
}
