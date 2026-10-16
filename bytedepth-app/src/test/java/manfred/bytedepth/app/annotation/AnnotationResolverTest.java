package manfred.bytedepth.app.annotation;

import static manfred.bytedepth.app.annotation.AnnotationAnchorFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import manfred.bytedepth.app.post.document.ArticleDocumentService;
import manfred.bytedepth.domain.annotation.*;
import org.junit.jupiter.api.Test;

class AnnotationResolverTest {
    private final ArticleDocumentService documents = new ArticleDocumentService();
    private final AnnotationResolver resolver = new AnnotationResolver();

    @Test
    void U01_sameRevisionReconstructsExactSpansWithoutChangingSource() {
        var source = documents.render("前文😀**目标原句**后文").document();
        var a = anchor(source, "目标原句");
        var result = resolver.resolve(1, a, source, 7, source);
        assertThat(result.status()).isEqualTo(PlacementStatus.ANCHORED);
        assertThat(result.spans()).isEqualTo(a.spans());
        assertThat(a.exact()).isEqualTo("目标原句");
    }

    @Test
    void U02_U03_prefixInsertionUnrelatedDeletionAndUniqueBlockMoveKeepIdentity() {
        var source = documents.render("无关段落\n\n唯一目标段：目标原句。\n\n尾部").document();
        var a = anchor(source, "目标原句");
        for (String markdown :
                List.of("新增前言\n\n无关段落\n\n唯一目标段：目标原句。\n\n尾部", "唯一目标段：目标原句。\n\n尾部", "尾部\n\n无关段落\n\n唯一目标段：目标原句。")) {
            var target = documents.render(markdown).document();
            var result = resolver.resolve(1, a, source, 8, target);
            assertThat(result.status()).isEqualTo(PlacementStatus.ANCHORED);
            var span = result.spans().getFirst();
            var segment = target.segments().stream()
                    .filter(s -> s.id().equals(span.segmentId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(segment.text()).isEqualTo("唯一目标段：目标原句。");
            assertThat(slice(segment.text(), span.start(), span.end())).isEqualTo("目标原句");
            assertThat(a.sourceRevision()).isEqualTo(7);
        }
    }

    @Test
    void U04_U05_internalInsertDeleteReplaceAndMissingTargetNeverRewriteQuote() {
        var source = documents.render("唯一目标段：目标原句。").document();
        var a = anchor(source, "目标原句");
        for (String md : List.of("仅剩邻句", "唯一目标段：目标新原句。", "唯一目标段：目标句。", "唯一目标段：另一句。")) {
            var result = resolver.resolve(1, a, source, 8, documents.render(md).document());
            assertThat(result.status()).isEqualTo(PlacementStatus.UNRESOLVED);
            assertThat(result.spans()).isEmpty();
            assertThat(a.exact()).isEqualTo("目标原句");
        }
    }

    @Test
    void U06_deletedOriginalMustNotJumpToRemainingIdenticalOccurrence() {
        var source = documents.render("同文目标\n\n同文目标").document();
        var a = anchorAt(source, 0, 4);
        var result = resolver.resolve(1, a, source, 8, documents.render("同文目标").document());
        assertThat(result.status()).isEqualTo(PlacementStatus.AMBIGUOUS);
        assertThat(result.spans()).isEmpty();
    }

    @Test
    void U07_oldOffsetsAndFirstCandidateCannotResolveEquivalentDuplicates() {
        var source = documents.render("同文目标\n\n同文目标").document();
        var a = anchorAt(source, 5, 4);
        assertThat(resolver.resolve(
                                1,
                                a,
                                source,
                                8,
                                documents.render("前言\n\n同文目标\n\n同文目标").document())
                        .status())
                .isEqualTo(PlacementStatus.AMBIGUOUS);
        var unique = documents.render("同文目标").document();
        assertThat(resolver.resolve(1, anchor(unique, "同文目标"), unique, 8, source)
                        .status())
                .isEqualTo(PlacementStatus.AMBIGUOUS);
    }

    @Test
    void U08_U25_approximateAndNormalizedUnicodeMatchesAreRejected() {
        for (var pair : List.of(
                List.of("quick brown fox", "quick brown fax"), List.of("café", "caf"), List.of("café", "cafe\u0301"))) {
            var source = documents.render(pair.getFirst()).document();
            assertThat(resolver.resolve(
                                    1,
                                    anchor(source, pair.getFirst()),
                                    source,
                                    8,
                                    documents.render(pair.getLast()).document())
                            .status())
                    .isEqualTo(PlacementStatus.UNRESOLVED);
        }
    }

    @Test
    void U10_crossBlockInlineWrappingMaintainsEveryRange() {
        var source = documents.render("前段目标\n\n后段目标").document();
        var a = anchor(source, "目标\n后段");
        var target = documents.render("插入\n\n前段**目标**\n\n后段目标").document();
        var result = resolver.resolve(1, a, source, 8, target);
        assertThat(result.status()).isEqualTo(PlacementStatus.ANCHORED);
        assertThat(result.spans()).hasSize(2);
    }

    @Test
    void U11_eachTargetVersionHasIndependentPlacementAndOriginalAnchorStaysImmutable() {
        var source = documents.render("目标原句").document();
        var a = anchor(source, "目标原句");
        var first =
                resolver.resolve(9, a, source, 8, documents.render("前言\n\n目标原句").document());
        var second = resolver.resolve(9, a, source, 9, documents.render("已删除").document());
        assertThat(first.targetRevision()).isEqualTo(8);
        assertThat(second.targetRevision()).isEqualTo(9);
        assertThat(first.status()).isEqualTo(PlacementStatus.ANCHORED);
        assertThat(second.status()).isEqualTo(PlacementStatus.UNRESOLVED);
        assertThat(a).isEqualTo(anchor(source, "目标原句"));
    }

    @Test
    void U12_missingOrConflictingRevisionModelsFailClosed() {
        var source = documents.render("目标").document();
        var a = anchor(source, "目标");
        assertThat(resolver.resolve(1, a, null, 8, source).reason()).isEqualTo(PlacementReason.MISSING_REVISION);
        assertThat(resolver.resolve(1, a, source, 8, null).reason()).isEqualTo(PlacementReason.MISSING_REVISION);
        var changed = documents.render("变化").document();
        assertThat(resolver.resolve(1, a, source, 7, changed).reason()).isEqualTo(PlacementReason.MODEL_MISMATCH);
        var unknown = new manfred.bytedepth.domain.post.document.ArticleDocument(
                2, source.digest(), source.text(), source.segments());
        assertThat(resolver.resolve(1, a, source, 8, unknown).reason()).isEqualTo(PlacementReason.MODEL_MISMATCH);
    }

    @Test
    void U45_candidateBudgetExhaustionKeepsRecordAndNoCandidateRange() {
        var source = documents.render("一\n\n二\n\n三").document();
        var a = anchor(source, "二");
        var small = new AnnotationResolver(new AnnotationResolver.Budget(1, 100, 1));
        var result = small.resolve(
                1, a, source, 8, documents.render("前言\n\n一\n\n二\n\n三").document());
        assertThat(result.reason()).isEqualTo(PlacementReason.BUDGET_EXCEEDED);
        assertThat(result.spans()).isEmpty();
        assertThat(a.exact()).isEqualTo("二");
    }

    @Test
    void U11_titleOnlyRevisionWithIdenticalModelKeepsDuplicatePositionsVerified() {
        var source = documents.render("同文目标\n\n同文目标").document();
        var a = anchorAt(source, 5, 4);
        assertThat(resolver.resolve(1, a, source, 8, source).status()).isEqualTo(PlacementStatus.ANCHORED);
    }

    @Test
    void U05_U10_selectedBlocksCannotBeReorderedOrHaveAnotherBlockInsertedBetween() {
        var source = documents.render("甲\n\n乙").document();
        var a = anchor(source, "甲\n乙");
        for (String md : List.of("乙\n\n甲", "甲\n\n插入\n\n乙")) {
            var result = resolver.resolve(1, a, source, 8, documents.render(md).document());
            assertThat(result.status()).isEqualTo(PlacementStatus.UNRESOLVED);
            assertThat(result.spans()).isEmpty();
        }
        var table = documents.render("| A | B |\n|---|---|").document();
        assertThat(resolver.resolve(
                                1,
                                anchor(table, "A\tB"),
                                table,
                                8,
                                documents.render("A\n\nB").document())
                        .reason())
                .isEqualTo(PlacementReason.QUOTE_CHANGED);
    }

    @Test
    void U12_invalidSavedAnchorAndInvalidTargetNeverThrowAwayTheRecord() {
        var source = documents.render("目标").document();
        var a = anchor(source, "目标");
        assertThat(resolver.resolve(1, with(a, "exact", "错误"), source, 8, source)
                        .status())
                .isEqualTo(PlacementStatus.UNRESOLVED);
        assertThat(resolver.resolve(1, a, source, 0, source).reason()).isEqualTo(PlacementReason.MODEL_MISMATCH);
        var fake =
                new manfred.bytedepth.domain.post.document.ArticleDocument(1, source.digest(), "篡改", source.segments());
        assertThat(resolver.resolve(1, a, source, 8, fake).reason()).isEqualTo(PlacementReason.MODEL_MISMATCH);
        var nullHash =
                new manfred.bytedepth.domain.post.document.ArticleDocument(1, null, source.text(), source.segments());
        assertThat(resolver.resolve(1, a, source, 8, nullHash).reason()).isEqualTo(PlacementReason.MODEL_MISMATCH);
        var badHash = new manfred.bytedepth.domain.post.document.ArticleDocument(
                1, "wrong", source.text(), source.segments());
        assertThat(resolver.resolve(1, a, source, 8, badHash).reason()).isEqualTo(PlacementReason.MODEL_MISMATCH);
        var missingText =
                new manfred.bytedepth.domain.post.document.ArticleDocument(1, source.digest(), null, source.segments());
        assertThat(resolver.resolve(1, a, source, 8, missingText).reason()).isEqualTo(PlacementReason.MODEL_MISMATCH);
    }

    @Test
    void U45_textAndCandidateBudgetsFailClosedAndPlacementsAreImmutable() {
        var source = documents.render("甲\n\n乙").document();
        var a = anchor(source, "甲\n乙");
        var target = documents.render("前\n\n甲\n\n乙").document();
        assertThat(new AnnotationResolver(new AnnotationResolver.Budget(100, 1, 100))
                        .resolve(1, a, source, 8, target)
                        .reason())
                .isEqualTo(PlacementReason.BUDGET_EXCEEDED);
        assertThat(new AnnotationResolver(new AnnotationResolver.Budget(100, 100, 1))
                        .resolve(1, a, source, 8, target)
                        .reason())
                .isEqualTo(PlacementReason.BUDGET_EXCEEDED);
        var placed = resolver.resolve(1, a, source, 7, source);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> placed.spans().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> a.spans().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
