package manfred.bytedepth.adapter.web.mobile;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.app.annotation.AnnotationView;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.post.document.SegmentSpan;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MobileAnnotationResponseMapper {
    private final MarkdownRenderer markdown;

    public Response map(AnnotationView view, Long user) {
        var a = view.annotation();
        var p = view.placement();
        String text = a.annotationText() == null ? "" : a.annotationText();
        return new Response(
                a.id().toString(),
                a.postId().toString(),
                a.selectedText(),
                text,
                markdown.render(text),
                a.color(),
                a.visibility(),
                utc(a.createdAt()),
                user != null && user.equals(a.userId()),
                a.anchor(),
                new Placement(
                        Long.toString(p.annotationId()),
                        p.targetRevision(),
                        p.projectionVersion(),
                        p.resolverVersion(),
                        p.status(),
                        p.spans(),
                        p.reason()),
                view.targetDigest(),
                a.migrationBaselineRevision());
    }

    static String utc(java.time.LocalDateTime time) {
        return time.atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toString();
    }

    public record Response(
            String id,
            String postId,
            String selectedText,
            String annotationText,
            String annotationHtml,
            String color,
            AnnotationVisibility visibility,
            String createdAt,
            boolean ownedByCurrentVisitor,
            AnnotationAnchor anchor,
            Placement placement,
            String targetDigest,
            Long migrationBaselineRevision) {}

    public record Placement(
            String annotationId,
            long targetRevision,
            int projectionVersion,
            int resolverVersion,
            PlacementStatus status,
            List<SegmentSpan> spans,
            PlacementReason reason) {}
}
