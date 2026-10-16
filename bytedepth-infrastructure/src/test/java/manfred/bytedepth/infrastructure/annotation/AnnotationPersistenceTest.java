package manfred.bytedepth.infrastructure.annotation;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import manfred.bytedepth.app.annotation.*;
import manfred.bytedepth.app.post.document.ArticleDocumentService;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.post.PostContentRevision;
import manfred.bytedepth.domain.post.document.SegmentSpan;
import manfred.bytedepth.infrastructure.post.*;
import org.junit.jupiter.api.Test;

class AnnotationPersistenceTest {
    @Test
    void U11_U37_revisionsAreImmutableAndPlacementsCannotCrossCacheKeys() throws Exception {
        try (var db = new AnnotationDatabaseFixture()) {
            var revisions = new MyBatisPostContentRevisionRepository(db.mapper(PostContentRevisionMapper.class));
            var model = new ArticleDocumentService().render("目标").document();
            var revision = new PostContentRevision(1, 7, "目标", model, true);
            assertThat(revisions.find(1, 7)).isEmpty();
            revisions.insert(revision);
            assertThat(revisions.find(1, 7)).contains(revision);
            assertThatThrownBy(() -> revisions.insert(new PostContentRevision(1, 7, "篡改", model, false)))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
            assertThat(revisions.find(1, 7)).contains(revision);
            var placements = new MyBatisAnnotationPlacementRepository(db.mapper(AnnotationPlacementMapper.class));
            var value = new AnnotationPlacement(
                    1, 8, 1, 1, PlacementStatus.ANCHORED, List.of(new SegmentSpan("s0", 0, 2)), PlacementReason.EXACT);
            placements.save(value);
            assertThat(placements.find(1, 8, 1, 1)).contains(value);
            assertThat(placements.find(1, 7, 1, 1)).isEmpty();
            assertThat(placements.find(1, 8, 2, 1)).isEmpty();
            assertThat(placements.find(1, 8, 1, 2)).isEmpty();
            var unresolved = new AnnotationPlacement(
                    1, 8, 1, 1, PlacementStatus.UNRESOLVED, List.of(), PlacementReason.TARGET_MISSING);
            placements.save(unresolved);
            assertThat(placements.find(1, 8, 1, 1)).contains(unresolved);
        }
    }

    @Test
    void U35_U40_sameRequestDifferentPayloadRejectedAndDeletedRequestReplayReturnsGone() throws Exception {
        try (var db = new AnnotationDatabaseFixture()) {
            var port = new MyBatisAnnotationRequestRepository(db.mapper(AnnotationRequestMapper.class));
            var ledger = new AnnotationRequestLedger(port);
            var scope = new AnnotationRequestPort.Scope(1, "u:3", UUID.randomUUID());
            String hash = "a".repeat(64);
            assertThat(ledger.lookup(scope, hash)).isEmpty();
            port.insert(scope, hash, 5);
            assertThat(ledger.lookup(scope, hash).orElseThrow().annotationId()).isEqualTo(5);
            assertThatThrownBy(() -> ledger.lookup(scope, "b".repeat(64)))
                    .isInstanceOf(AnnotationWriteConflictException.class)
                    .satisfies(error -> assertThat(((AnnotationWriteConflictException) error).kind())
                            .isEqualTo(AnnotationWriteConflictException.Kind.REQUEST_REUSED));
            assertThatThrownBy(() -> port.insert(scope, hash, 6))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
            port.markDeleted(5);
            assertThat(port.find(scope).orElseThrow().deleted()).isTrue();
            assertThatThrownBy(() -> ledger.lookup(scope, hash))
                    .isInstanceOf(AnnotationWriteConflictException.class)
                    .satisfies(error -> assertThat(((AnnotationWriteConflictException) error).kind())
                            .isEqualTo(AnnotationWriteConflictException.Kind.DELETED_REQUEST));
            assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM annotation_request", Integer.class))
                    .isEqualTo(1);
            assertThat(port.find(new AnnotationRequestPort.Scope(2, "u:3", scope.requestId())))
                    .isEmpty();
            assertThat(port.find(new AnnotationRequestPort.Scope(1, "u:4", scope.requestId())))
                    .isEmpty();
        }
    }

    @Test
    void U38_U41_legacyUnverifiedRowsAreOwnerOnlyAndNormalEditsCannotChangeOriginalAnchors() throws Exception {
        try (var db = new AnnotationDatabaseFixture()) {
            var repository = new MyBatisAnnotationRepository(db.mapper(PostAnnotationMapper.class));
            var legacy = repository.save(new PostAnnotation(
                    null,
                    1L,
                    3L,
                    null,
                    "目标",
                    "评论",
                    "yellow",
                    AnnotationVisibility.PUBLIC,
                    0,
                    2,
                    LocalDateTime.now(),
                    false));
            var hidden = repository.save(new PostAnnotation(
                    null,
                    1L,
                    null,
                    "a".repeat(64),
                    "丢失",
                    "保留",
                    "blue",
                    AnnotationVisibility.PUBLIC,
                    0,
                    2,
                    LocalDateTime.now(),
                    true));
            assertThat(repository.findVisibleByPostId(1L, null, null)).isEmpty();
            assertThat(repository.findVisibleByPostId(1L, 3L, null))
                    .extracting(PostAnnotation::id)
                    .containsExactly(legacy.id());
            assertThat(repository.findVisibleByPostId(1L, null, "a".repeat(64)))
                    .extracting(PostAnnotation::id)
                    .containsExactly(hidden.id());
            repository.initializeAnchor(hidden.id(), null, 7);
            assertThat(repository.findById(hidden.id()).orElseThrow().migrationBaselineRevision())
                    .isEqualTo(7);
            var model = new ArticleDocumentService().render("目标").document();
            var anchor = new AnnotationAnchor(
                    1,
                    7,
                    1,
                    model.digest(),
                    "目标",
                    "",
                    "",
                    0,
                    2,
                    List.of(new SegmentSpan("s0", 0, 2)),
                    UUID.randomUUID());
            repository.initializeAnchor(legacy.id(), anchor, 7);
            var verified = repository.findById(legacy.id()).orElseThrow();
            assertThat(verified.migrationBaselineRevision()).isEqualTo(7);
            assertThat(repository.findVisibleByPostId(1L, null, null))
                    .extracting(PostAnnotation::id)
                    .containsExactly(legacy.id());
            assertThatThrownBy(() -> repository.initializeAnchor(legacy.id(), anchor, 8))
                    .isInstanceOf(IllegalStateException.class);
            var returned = repository.update(new PostAnnotation(
                    legacy.id(),
                    1L,
                    3L,
                    null,
                    "错误引用",
                    null,
                    "green",
                    AnnotationVisibility.PRIVATE,
                    99,
                    100,
                    LocalDateTime.now(),
                    true));
            assertThat(returned.selectedText()).isEqualTo("目标");
            assertThat(returned.anchor()).isEqualTo(anchor);
            var edited = repository.findById(legacy.id()).orElseThrow();
            assertThat(edited.anchor()).isEqualTo(anchor);
            assertThat(edited.selectedText()).isEqualTo("目标");
            assertThat(edited.startOffset()).isZero();
            assertThat(edited.deleted()).isFalse();
            assertThat(edited.annotationText()).isNull();
            assertThat(repository.findVisibleByPostId(1L, null, null)).isEmpty();
            assertThat(repository.findVisibleByPostId(2L, 3L, null)).isEmpty();
        }
    }

    @Test
    void U38_protocolPublicPrivateAndAnonymousVisibilityMatrixUsesRealSql() throws Exception {
        try (var db = new AnnotationDatabaseFixture()) {
            var repository = new MyBatisAnnotationRepository(db.mapper(PostAnnotationMapper.class));
            var model = new ArticleDocumentService().render("目标").document();
            var anchor = new AnnotationAnchor(
                    1,
                    7,
                    1,
                    model.digest(),
                    "目标",
                    "",
                    "",
                    0,
                    2,
                    List.of(new SegmentSpan("s0", 0, 2)),
                    UUID.randomUUID());
            var publicRow = repository.save(new PostAnnotation(
                    null,
                    1L,
                    3L,
                    null,
                    "目标",
                    "公开",
                    "yellow",
                    AnnotationVisibility.PUBLIC,
                    0,
                    2,
                    LocalDateTime.now(),
                    false,
                    anchor,
                    null));
            var privateRow = repository.save(new PostAnnotation(
                    null,
                    1L,
                    4L,
                    null,
                    "目标",
                    "私有",
                    "green",
                    AnnotationVisibility.PRIVATE,
                    0,
                    2,
                    LocalDateTime.now(),
                    false,
                    anchor,
                    null));
            var anonymous = repository.save(new PostAnnotation(
                    null,
                    1L,
                    null,
                    "a".repeat(64),
                    "目标",
                    null,
                    "blue",
                    AnnotationVisibility.PRIVATE,
                    0,
                    2,
                    LocalDateTime.now(),
                    false,
                    anchor,
                    null));
            assertThat(publicRow.anchor()).isEqualTo(anchor);
            assertThat(publicRow.migrationBaselineRevision()).isNull();
            assertThat(repository.findVisibleByPostId(1L, null, null))
                    .extracting(PostAnnotation::id)
                    .containsExactly(publicRow.id());
            assertThat(repository.findVisibleByPostId(1L, 3L, null))
                    .extracting(PostAnnotation::id)
                    .containsExactly(publicRow.id());
            assertThat(repository.findVisibleByPostId(1L, 4L, null))
                    .extracting(PostAnnotation::id)
                    .containsExactly(publicRow.id(), privateRow.id());
            assertThat(repository.findVisibleByPostId(1L, null, "a".repeat(64)))
                    .extracting(PostAnnotation::id)
                    .containsExactly(publicRow.id(), anonymous.id());
            assertThat(repository.findVisibleByPostId(1L, null, "b".repeat(64)))
                    .extracting(PostAnnotation::id)
                    .containsExactly(publicRow.id());
            repository.delete(publicRow.id());
            assertThat(repository.findById(publicRow.id())).isEmpty();
            assertThatThrownBy(() -> repository.update(publicRow))
                    .isInstanceOf(manfred.bytedepth.domain.common.DomainException.class);
            assertThat(repository.findByPostId(1L)).hasSize(2);
        }
    }
}
