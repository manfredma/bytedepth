package manfred.bytedepth.app.post.document;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.annotation.AnnotationRepositoryPort;
import manfred.bytedepth.app.annotation.LegacyAnchorMigrator;
import manfred.bytedepth.app.post.PostContentRevisionPort;
import manfred.bytedepth.app.post.PostWriteTransaction;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostContentRevision;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArticleRevisionService {
    private final PostWriteTransaction transactions;
    private final PostContentRevisionPort revisions;
    private final ArticleDocumentService documents;
    private final AnnotationRepositoryPort annotations;
    private final LegacyAnchorMigrator migrator;

    public record RevisionedArticle(Post post, PostContentRevision revision, RenderedArticle rendered) {}

    public RevisionedArticle current(long postId) {
        return transactions.withLockedPost(postId, this::ensureCurrent);
    }

    /** Caller must hold the post row lock; also used before and after a content update. */
    public RevisionedArticle ensureCurrent(Post post) {
        return ensureCurrent(post, true);
    }

    public RevisionedArticle capture(Post post) {
        return ensureCurrent(post, false);
    }

    private RevisionedArticle ensureCurrent(Post post, boolean baseline) {
        var rendered = documents.render(post.getContent());
        var existing = revisions.find(post.getId(), post.getContentVersion());
        PostContentRevision revision;
        if (existing.isPresent()) {
            revision = existing.get();
            if (!java.util.Objects.equals(revision.markdown(), post.getContent())
                    || !revision.document().equals(rendered.document()))
                throw new IllegalStateException("Immutable article revision mismatch");
        } else {
            revision = new PostContentRevision(
                    post.getId(), post.getContentVersion(), post.getContent(), rendered.document(), baseline);
            revisions.insert(revision);
        }
        for (var annotation : annotations.findByPostId(post.getId())) {
            if (annotation.anchor() == null && annotation.migrationBaselineRevision() == null) {
                var migrated = migrator.migrate(annotation, revision);
                annotations.initializeAnchor(annotation.id(), migrated.anchor(), revision.contentVersion());
            }
        }
        return new RevisionedArticle(post, revision, rendered);
    }
}
