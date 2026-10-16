package manfred.bytedepth.infrastructure.post;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.PostContentRevisionPort;
import manfred.bytedepth.domain.post.PostContentRevision;
import manfred.bytedepth.domain.post.document.ArticleDocument;
import manfred.bytedepth.infrastructure.annotation.AnnotationJson;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisPostContentRevisionRepository implements PostContentRevisionPort {
    private final PostContentRevisionMapper mapper;

    @Override
    public Optional<PostContentRevision> find(long postId, long revision) {
        return Optional.ofNullable(mapper.find(postId, revision))
                .map(row -> new PostContentRevision(
                        row.getPostId(),
                        row.getContentVersion(),
                        row.getMarkdown(),
                        AnnotationJson.read(row.getDocumentJson(), ArticleDocument.class),
                        Boolean.TRUE.equals(row.getBaseline())));
    }

    @Override
    public void insert(PostContentRevision revision) {
        var row = new PostContentRevisionDO();
        row.setPostId(revision.postId());
        row.setContentVersion(revision.contentVersion());
        row.setMarkdown(revision.markdown());
        row.setDocumentJson(AnnotationJson.write(revision.document()));
        row.setBaseline(revision.baseline());
        mapper.insert(row);
    }
}
