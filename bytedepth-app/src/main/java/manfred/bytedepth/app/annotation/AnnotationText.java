package manfred.bytedepth.app.annotation;

import java.util.ArrayList;
import java.util.List;
import manfred.bytedepth.domain.post.document.ArticleDocument;
import manfred.bytedepth.domain.post.document.SegmentSpan;

final class AnnotationText {
    private AnnotationText() {}

    static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    static String slice(String text, int start, int end) {
        return text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
    }

    static List<SegmentSpan> spans(ArticleDocument doc, int start, int end) {
        var result = new ArrayList<SegmentSpan>();
        for (var s : doc.segments()) {
            int lo = Math.max(start, s.start()), hi = Math.min(end, s.end());
            if (lo < hi) result.add(new SegmentSpan(s.id(), lo - s.start(), hi - s.start()));
        }
        return List.copyOf(result);
    }
}
