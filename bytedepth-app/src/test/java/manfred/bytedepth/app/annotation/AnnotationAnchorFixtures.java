package manfred.bytedepth.app.annotation;

import java.util.ArrayList;
import java.util.UUID;
import manfred.bytedepth.domain.annotation.AnnotationAnchor;
import manfred.bytedepth.domain.post.document.ArticleDocument;
import manfred.bytedepth.domain.post.document.SegmentSpan;

final class AnnotationAnchorFixtures {
    static AnnotationAnchor anchor(ArticleDocument doc, String quote) {
        int utf16 = doc.text().indexOf(quote);
        return anchorAt(doc, doc.text().codePointCount(0, utf16), quote.codePointCount(0, quote.length()));
    }

    static AnnotationAnchor anchorAt(ArticleDocument doc, int start, int length) {
        int end = start + length;
        var spans = new ArrayList<SegmentSpan>();
        for (var segment : doc.segments()) {
            int lo = Math.max(start, segment.start()), hi = Math.min(end, segment.end());
            if (lo < hi) spans.add(new SegmentSpan(segment.id(), lo - segment.start(), hi - segment.start()));
        }
        return new AnnotationAnchor(
                1,
                7,
                1,
                doc.digest(),
                slice(doc.text(), start, end),
                slice(doc.text(), Math.max(0, start - 128), start),
                slice(
                        doc.text(),
                        end,
                        Math.min(doc.text().codePointCount(0, doc.text().length()), end + 128)),
                start,
                end,
                spans,
                UUID.fromString("00000000-0000-4000-8000-000000000001"));
    }

    static String slice(String text, int start, int end) {
        return text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
    }

    static AnnotationAnchor with(AnnotationAnchor anchor, String field, Object value) {
        try {
            var parts = AnnotationAnchor.class.getRecordComponents();
            var types = new Class<?>[parts.length];
            var values = new Object[parts.length];
            boolean found = false;
            for (int i = 0; i < parts.length; i++) {
                types[i] = parts[i].getType();
                values[i] = parts[i].getAccessor().invoke(anchor);
                if (parts[i].getName().equals(field)) {
                    values[i] = value;
                    found = true;
                }
            }
            if (!found) throw new IllegalArgumentException("Unknown anchor field " + field);
            return AnnotationAnchor.class.getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
