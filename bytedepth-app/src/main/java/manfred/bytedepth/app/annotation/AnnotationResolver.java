package manfred.bytedepth.app.annotation;

import static manfred.bytedepth.app.annotation.AnnotationText.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.post.document.ArticleDocument;
import manfred.bytedepth.domain.post.document.SegmentSpan;
import org.springframework.stereotype.Component;

/** Exact evidence only; old offsets and candidate ranking never decide cross-revision identity. */
@Component
public class AnnotationResolver {
    public record Budget(int maxBlocks, int maxCodepoints, int maxCandidates) {}

    private record Block(String kind, String text, int start, int end, int index) {
        String key() {
            return kind + "\0" + text;
        }
    }

    private final Budget budget;
    private final AnchorValidator validator = new AnchorValidator();

    public AnnotationResolver() {
        this(new Budget(10000, 2000000, 10000));
    }

    public AnnotationResolver(Budget budget) {
        this.budget = budget;
    }

    public AnnotationPlacement resolve(
            long id, AnnotationAnchor anchor, ArticleDocument source, long targetRevision, ArticleDocument target) {
        if (source == null || target == null) return failed(id, targetRevision, PlacementReason.MISSING_REVISION);
        try {
            validator.validate(anchor, source);
        } catch (InvalidAnchorException invalid) {
            return failed(id, targetRevision, invalid.reason());
        }
        if (targetRevision < 1
                || target.projectionVersion() != 1
                || target.text() == null
                || target.digest() == null
                || !target.digest().matches("[0-9a-f]{64}"))
            return failed(id, targetRevision, PlacementReason.MODEL_MISMATCH);
        if (source.equals(target)) return anchored(id, targetRevision, anchor.spans());
        if (targetRevision == anchor.sourceRevision() || source.digest().equals(target.digest()))
            return failed(id, targetRevision, PlacementReason.MODEL_MISMATCH);
        if ((long) length(source.text()) + length(target.text()) > budget.maxCodepoints())
            return failed(id, targetRevision, PlacementReason.BUDGET_EXCEEDED);
        var oldBlocks = blocks(source);
        var newBlocks = blocks(target);
        if ((long) oldBlocks.size() + newBlocks.size() > budget.maxBlocks())
            return failed(id, targetRevision, PlacementReason.BUDGET_EXCEEDED);
        var oldByKey = index(oldBlocks);
        var newByKey = index(newBlocks);
        List<Block> selected = oldBlocks.stream()
                .filter(b -> b.start() < anchor.end() && b.end() > anchor.start())
                .toList();
        var matched = new ArrayList<Block>();
        int comparisons = 0;
        for (var block : selected) {
            var candidates = newByKey.getOrDefault(block.key(), List.of());
            comparisons += candidates.size();
            if (comparisons > budget.maxCandidates())
                return failed(id, targetRevision, PlacementReason.BUDGET_EXCEEDED);
            if (candidates.isEmpty()) return failed(id, targetRevision, PlacementReason.TARGET_MISSING);
            if (oldByKey.get(block.key()).size() != 1 || candidates.size() != 1)
                return failed(id, targetRevision, PlacementReason.DUPLICATE_TARGET);
            if (!matched.isEmpty()
                    && candidates.getFirst().index() != matched.getLast().index() + 1)
                return failed(id, targetRevision, PlacementReason.QUOTE_CHANGED);
            matched.add(candidates.getFirst());
        }
        int start = matched.getFirst().start()
                + anchor.start()
                - selected.getFirst().start();
        int end = matched.getLast().start() + anchor.end() - selected.getLast().start();
        if (!slice(target.text(), start, end).equals(anchor.exact()))
            return failed(id, targetRevision, PlacementReason.QUOTE_CHANGED);
        return anchored(id, targetRevision, spans(target, start, end));
    }

    private AnnotationPlacement anchored(long id, long revision, List<SegmentSpan> spans) {
        return new AnnotationPlacement(id, revision, 1, 1, PlacementStatus.ANCHORED, spans, PlacementReason.EXACT);
    }

    private AnnotationPlacement failed(long id, long revision, PlacementReason reason) {
        return new AnnotationPlacement(
                id,
                revision,
                1,
                1,
                reason == PlacementReason.DUPLICATE_TARGET ? PlacementStatus.AMBIGUOUS : PlacementStatus.UNRESOLVED,
                List.of(),
                reason);
    }

    private List<Block> blocks(ArticleDocument doc) {
        var result = new ArrayList<Block>();
        var segments = doc.segments();
        int first = 0;
        while (first < segments.size()) {
            var head = segments.get(first);
            var text = new StringBuilder(head.text());
            int last = first + 1;
            while (last < segments.size() && segments.get(last).blockId().equals(head.blockId())) {
                text.append(segments.get(last).text());
                last++;
            }
            result.add(new Block(
                    head.kind(),
                    text.toString(),
                    head.start(),
                    segments.get(last - 1).end(),
                    result.size()));
            first = last;
        }
        return result;
    }

    private Map<String, List<Block>> index(List<Block> blocks) {
        var index = new HashMap<String, List<Block>>();
        for (var block : blocks)
            index.computeIfAbsent(block.key(), ignored -> new ArrayList<>()).add(block);
        return index;
    }
}
