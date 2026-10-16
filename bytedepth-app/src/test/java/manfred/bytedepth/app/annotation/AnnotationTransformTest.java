package manfred.bytedepth.app.annotation;

import static manfred.bytedepth.app.annotation.AnnotationAnchorFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import manfred.bytedepth.app.post.document.ArticleDocumentService;
import manfred.bytedepth.domain.annotation.PlacementStatus;
import org.junit.jupiter.api.Test;

class AnnotationTransformTest {
    @Test
    void U44_seededTransformationsRecoverTheUniqueSurvivingTargetIdentity() {
        var random = new Random(20261016);
        var documents = new ArticleDocumentService();
        var resolver = new AnnotationResolver();
        for (int run = 0; run < 200; run++) {
            String target = "identity-" + run + ": 😀anchorProbe café";
            var blocks = new ArrayList<>(List.of("other-A-" + run, "other-B-" + run, target, "other-C-" + run));
            var source = documents.render(String.join("\n\n", blocks)).document();
            var a = anchor(source, "anchorProbe");
            Collections.shuffle(blocks, random);
            if (run % 2 == 0) blocks.addFirst("inserted-" + run);
            if (run % 3 == 0) blocks.remove("other-A-" + run);
            String markdown = String.join("\n\n", blocks).replace("😀anchorProbe café", "😀**anchorProbe** café");
            var doc = documents.render(markdown).document();
            var result = resolver.resolve(run + 1, a, source, 8, doc);
            assertThat(result.status()).as("seeded identity %s", run).isEqualTo(PlacementStatus.ANCHORED);
            var span = result.spans().getFirst();
            var segment = doc.segments().stream()
                    .filter(s -> s.id().equals(span.segmentId()))
                    .findFirst()
                    .orElseThrow();
            int actual = segment.start() + span.start();
            int expected = doc.text().codePointCount(0, doc.text().indexOf(target))
                    + target.codePointCount(0, target.indexOf("anchorProbe"));
            assertThat(actual).as("exact independent target location %s", run).isEqualTo(expected);
            assertThat(a.exact()).isEqualTo("anchorProbe");
        }
    }
}
