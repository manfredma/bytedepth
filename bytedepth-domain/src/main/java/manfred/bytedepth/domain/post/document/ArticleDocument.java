package manfred.bytedepth.domain.post.document;

import java.util.List;

public record ArticleDocument(int projectionVersion, String digest, String text, List<DocumentSegment> segments) {
    public ArticleDocument {
        segments = List.copyOf(segments);
    }
}
