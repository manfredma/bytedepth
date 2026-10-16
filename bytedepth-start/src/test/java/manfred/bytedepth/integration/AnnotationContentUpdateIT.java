package manfred.bytedepth.integration;

import static org.junit.jupiter.api.Assertions.*;

import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.annotation.PlacementStatus;
import org.junit.jupiter.api.Test;

class AnnotationContentUpdateIT extends AnnotationITSupport {
    @Test
    void paragraphInsertionReanchorsWithoutRewritingOriginalEvidence() {
        var post = draft("第一段\n\n目标句子");
        var saved = annotate(post, "目标句子", AnnotationVisibility.PUBLIC);
        var original = annotations.findById(saved.annotation().id()).orElseThrow();
        update.execute(post.getId(), post.getTitle(), "第一段\n\n新增段\n\n目标句子");
        var view =
                query.viewCurrent(annotations.findById(saved.annotation().id()).orElseThrow());
        assertEquals(PlacementStatus.ANCHORED, view.placement().status());
        assertEquals(original, view.annotation());
        assertEquals(post.getContentVersion() + 1, view.placement().targetRevision());
        assertEquals("目标句子", view.annotation().selectedText());
        assertFalse(view.annotation().deleted());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT target_revision) FROM annotation_placement WHERE annotation_id=?",
                        Integer.class,
                        original.id()));
        assertEquals(1, query.views(post.getId(), null, null).size());
    }

    @Test
    void deletedAndReplacedSelectionsKeepTheirQuoteAndCommentsUnresolved() {
        for (String replacement : java.util.List.of("保留内容", "保留内容\n\n新方案")) {
            var post = draft("保留内容\n\n旧方案");
            var saved = annotate(post, "旧方案", AnnotationVisibility.PUBLIC);
            var original = annotations.findById(saved.annotation().id()).orElseThrow();
            update.execute(post.getId(), post.getTitle(), replacement);
            var view = query.viewCurrent(
                    annotations.findById(saved.annotation().id()).orElseThrow());
            assertEquals(PlacementStatus.UNRESOLVED, view.placement().status());
            assertEquals(original, view.annotation());
            assertEquals("旧方案", view.annotation().selectedText());
            assertEquals("原始评注", view.annotation().annotationText());
            assertFalse(view.annotation().deleted());
            assertEquals(1, query.views(post.getId(), null, null).size());
        }
    }

    @Test
    void privateAnchoredAndUnresolvedRowsHaveTheSameOwnerBoundary() {
        var post = draft("目标句子");
        var saved = annotate(post, "目标句子", AnnotationVisibility.PRIVATE);
        var original = annotations.findById(saved.annotation().id()).orElseThrow();
        for (String body : java.util.List.of("目标句子", "替换正文")) {
            update.execute(post.getId(), "版本更新", body);
            assertTrue(query.views(post.getId(), null, null).isEmpty());
            assertTrue(query.views(post.getId(), null, "b".repeat(64)).isEmpty());
            assertTrue(query.views(post.getId(), 1L, null).isEmpty());
            var owned = query.views(post.getId(), null, owner);
            assertEquals(1, owned.size());
            assertEquals(original, owned.getFirst().annotation());
            assertEquals(
                    body.equals("目标句子") ? PlacementStatus.ANCHORED : PlacementStatus.UNRESOLVED,
                    owned.getFirst().placement().status());
        }
    }
}
