package manfred.bytedepth.app.annotation;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import manfred.bytedepth.app.post.document.ArticleDocumentService;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.post.PostContentRevision;
import org.junit.jupiter.api.Test;

class LegacyAnchorMigratorTest {
    private final LegacyAnchorMigrator migrator = new LegacyAnchorMigrator();

    private PostAnnotation legacy(String quote, int start, int end, boolean hidden) {
        return new PostAnnotation(
                1L,
                1L,
                3L,
                null,
                quote,
                "原评论",
                "yellow",
                AnnotationVisibility.PUBLIC,
                start,
                end,
                LocalDateTime.of(2020, 1, 1, 0, 0),
                hidden);
    }

    private PostContentRevision baseline(String markdown) {
        return new PostContentRevision(
                1, 7, markdown, new ArticleDocumentService().render(markdown).document(), true);
    }

    @Test
    void U41_verifiedLegacyCoordinatesUseAnExplicitMigrationBaselineNotFabricatedCreationHistory() {
        var original = legacy("目标", 3, 5, false);
        var result = migrator.migrate(original, baseline("前😀目标后"));
        assertThat(result.anchor().exact()).isEqualTo("目标");
        assertThat(result.anchor().start()).isEqualTo(2);
        assertThat(result.migrationBaselineRevision()).isEqualTo(7);
        assertThat(result.createdAt()).isEqualTo(original.createdAt());
        assertThat(result.selectedText()).isEqualTo(original.selectedText());
        assertThat(result.startOffset()).isEqualTo(3);
        assertThat(migrator.migrate(result, baseline("已变化"))).isEqualTo(result);
    }

    @Test
    void U41_missingEvidenceDuplicateWrongCoordinatesAndAutoHiddenNeverBecomePublicAgain() {
        for (var original :
                java.util.List.of(legacy("目标", 0, 2, true), legacy("目标", 1, 3, false), legacy("不存在", 0, 3, false))) {
            var result = migrator.migrate(original, baseline("目标"));
            assertThat(result.anchor()).isNull();
            assertThat(result.migrationBaselineRevision()).isEqualTo(7);
            assertThat(result.annotationText()).isEqualTo("原评论");
            assertThat(migrator.migrate(result, baseline("目标"))).isEqualTo(result);
        }
        assertThat(migrator.migrate(legacy("目标", 0, 2, false), baseline("目标\n\n目标"))
                        .anchor())
                .isNull();
        var original = legacy("目标", 0, 2, false);
        assertThat(migrator.migrate(original, null)).isEqualTo(original);
    }

    @Test
    void U39_migrationCannotBorrowAnotherArticlesRevision() {
        var original = legacy("目标", 0, 2, false);
        var source = baseline("目标");
        var foreign = new PostContentRevision(2, 7, source.markdown(), source.document(), true);
        assertThat(migrator.migrate(original, foreign)).isEqualTo(original);
    }

    @Test
    void U41_invalidLegacyAndUnsupportedSourceAreQuarantinedWithoutLosingComments() {
        for (var old : java.util.List.of(
                legacy(null, 0, 1, false),
                legacy("", 0, 1, false),
                legacy("目标", -1, 1, false),
                legacy("目标", 0, 0, false),
                legacy("目标", 0, 99, false),
                legacy("错字", 0, 2, false))) {
            var result = migrator.migrate(old, baseline("目标"));
            assertThat(result.anchor()).isNull();
            assertThat(result.annotationText()).isEqualTo("原评论");
        }
        assertThat(migrator.migrate(legacy("graph", 0, 5, false), baseline("```mermaid\ngraph TD; A-->B\n```"))
                        .anchor())
                .isNull();
        assertThat(migrator.migrate(legacy("1", 2, 3, false), baseline("正文[^a]\n\n[^a]: 解释"))
                        .anchor())
                .isNull();
        var old = legacy("目标", 0, 2, false);
        var source = baseline("目标");
        assertThat(migrator.migrate(old, new PostContentRevision(1, 0, source.markdown(), source.document(), true)))
                .isEqualTo(old);
        assertThat(migrator.migrate(old, new PostContentRevision(1, 7, source.markdown(), null, true)))
                .isEqualTo(old);
    }
}
