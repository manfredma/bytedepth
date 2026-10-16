package manfred.bytedepth.app.annotation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;
import manfred.bytedepth.app.post.*;
import manfred.bytedepth.app.post.document.*;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.post.*;
import org.junit.jupiter.api.Test;

class AnnotationCommandProtocolTest {
    final AnnotationRepositoryPort annotations = mock(AnnotationRepositoryPort.class);
    final AnnotationRequestPort requests = mock(AnnotationRequestPort.class);
    final PostContentRevisionPort revisions = mock(PostContentRevisionPort.class);
    final AnnotationPlacementPort placements = mock(AnnotationPlacementPort.class);
    final ArticleDocumentService documents = new ArticleDocumentService();
    final Post post = Post.reconstruct(
            1L,
            "slug",
            "title",
            "😀原文",
            PostStatus.PUBLISHED,
            LocalDateTime.now(),
            null,
            LocalDateTime.now(),
            null,
            2L,
            false,
            7);
    final PostWriteTransaction tx = new PostWriteTransaction() {
        public <T> T withLockedPost(Long id, Function<Post, T> action) {
            return action.apply(post);
        }
    };
    final ArticleRevisionService current =
            new ArticleRevisionService(tx, revisions, documents, annotations, new LegacyAnchorMigrator());
    final ListAnnotationsQryExe query =
            new ListAnnotationsQryExe(annotations, current, revisions, placements, new AnnotationResolver());
    final CreateAnnotationCmdExe create = new CreateAnnotationCmdExe(
            annotations,
            tx,
            current,
            new AnchorValidator(),
            requests,
            new AnnotationRequestLedger(requests),
            query,
            documents);
    final AnnotationAnchor anchor =
            AnnotationAnchorFixtures.anchor(documents.render(post.getContent()).document(), "😀原文");

    CreateAnnotationRequest request(AnnotationAnchor a) {
        return new CreateAnnotationRequest(a, "评论", "yellow", AnnotationVisibility.PUBLIC);
    }

    void save() {
        when(revisions.find(1, 7))
                .thenReturn(Optional.of(new PostContentRevision(
                        1,
                        7,
                        post.getContent(),
                        documents.render(post.getContent()).document(),
                        false)));
        when(annotations.save(any())).thenAnswer(inv -> {
            PostAnnotation a = inv.getArgument(0);
            return new PostAnnotation(
                    9L,
                    a.postId(),
                    a.userId(),
                    a.ownerTokenHash(),
                    a.selectedText(),
                    a.annotationText(),
                    a.color(),
                    a.visibility(),
                    a.startOffset(),
                    a.endOffset(),
                    a.createdAt(),
                    false,
                    a.anchor(),
                    null);
        });
    }

    @Test
    void U01_U35_sameRequestCreatesOnlyOnceAndNeverChangesOriginal() {
        save();
        var result = create.execute(1L, 2L, null, request(anchor));
        assertEquals(anchor, result.annotation().anchor());
        assertEquals(PlacementStatus.ANCHORED, result.placement().status());
        var scope = new AnnotationRequestPort.Scope(1, "user:2", anchor.requestId());
        var entry = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(requests).insert(eq(scope), entry.capture(), eq(9L));
        when(requests.find(scope))
                .thenReturn(Optional.of(new AnnotationRequestPort.Entry(scope, entry.getValue(), 9, false)));
        when(annotations.findById(9L)).thenReturn(Optional.of(result.annotation()));
        assertEquals(
                9, create.execute(1L, 2L, null, request(anchor)).annotation().id());
        verify(annotations, times(1)).save(any());
        assertEquals(
                AnnotationWriteConflictException.Kind.REQUEST_REUSED,
                assertThrows(
                                AnnotationWriteConflictException.class,
                                () -> create.execute(
                                        1L,
                                        2L,
                                        null,
                                        new CreateAnnotationRequest(
                                                anchor, "另一个评论", "yellow", AnnotationVisibility.PUBLIC)))
                        .kind());
        when(requests.find(scope))
                .thenReturn(Optional.of(new AnnotationRequestPort.Entry(scope, entry.getValue(), 9, true)));
        assertEquals(
                AnnotationWriteConflictException.Kind.DELETED_REQUEST,
                assertThrows(
                                AnnotationWriteConflictException.class,
                                () -> create.execute(1L, 2L, null, request(anchor)))
                        .kind());
    }

    @Test
    void U35_changedPayloadCannotCollideThroughInvalidUnicodeEncoding() {
        post.updateContent("title", "?正文");
        var doc = documents.render(post.getContent()).document();
        var a = AnnotationAnchorFixtures.with(AnnotationAnchorFixtures.anchor(doc, "正文"), "sourceRevision", 8L);
        save();
        when(revisions.find(1, 8))
                .thenReturn(Optional.of(new PostContentRevision(1, 8, post.getContent(), doc, false)));
        var first = create.execute(1L, 2L, null, request(a));
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        var scope = new AnnotationRequestPort.Scope(1, "user:2", a.requestId());
        verify(requests).insert(eq(scope), hash.capture(), eq(9L));
        when(requests.find(scope))
                .thenReturn(Optional.of(new AnnotationRequestPort.Entry(scope, hash.getValue(), 9, false)));
        when(annotations.findById(9L)).thenReturn(Optional.of(first.annotation()));
        var forged = AnnotationAnchorFixtures.with(a, "prefix", String.valueOf((char) 0xD800));
        assertEquals(
                AnnotationWriteConflictException.Kind.REQUEST_REUSED,
                assertThrows(
                                AnnotationWriteConflictException.class,
                                () -> create.execute(1L, 2L, null, request(forged)))
                        .kind());
    }

    @Test
    void U39_postDeletedBeforeLockCannotAcceptNewAnnotations() {
        post.delete();
        assertThrows(java.util.NoSuchElementException.class, () -> create.execute(1L, 2L, null, request(anchor)));
        verifyNoInteractions(annotations, revisions, requests);
    }

    @Test
    void U33_U36_lockedLatestVersionRejectsStaleAndWritesNothing() {
        post.updateContent("new", "新正文");
        assertEquals(
                AnnotationWriteConflictException.Kind.STALE_REVISION,
                assertThrows(
                                AnnotationWriteConflictException.class,
                                () -> create.execute(1L, 2L, null, request(anchor)))
                        .kind());
        verifyNoInteractions(annotations, revisions);
        verify(requests, never()).insert(any(), any(), anyLong());
    }

    @Test
    void U12_U34_invalidSelectorAndOldClientsWriteNothing() {
        assertThrows(InvalidAnchorException.class, () -> create.execute(1L, 2L, null, request(null)));
        for (var invalid : java.util.List.of(
                AnnotationAnchorFixtures.with(anchor, "exact", "伪造"),
                AnnotationAnchorFixtures.with(anchor, "digest", "f".repeat(64)),
                AnnotationAnchorFixtures.with(anchor, "schemaVersion", 99),
                AnnotationAnchorFixtures.with(anchor, "requestId", null))) {
            assertThrows(InvalidAnchorException.class, () -> create.execute(1L, 2L, null, request(invalid)));
        }
        verify(annotations, never()).save(any());
        verify(requests, never()).insert(any(), any(), anyLong());
    }

    @Test
    void U04_U11_resolveDeletedTextKeepsCommentAndOriginalAnchor() {
        save();
        var original = create.execute(1L, 2L, null, request(anchor)).annotation();
        post.updateContent("title", "另一段");
        when(revisions.find(1, 7))
                .thenReturn(Optional.of(new PostContentRevision(
                        1, 7, "😀原文", documents.render("😀原文").document(), false)));
        var result = query.view(original, current.current(1).revision());
        assertEquals(PlacementStatus.UNRESOLVED, result.placement().status());
        assertEquals("评论", result.annotation().annotationText());
        assertEquals(anchor, result.annotation().anchor());
        verify(annotations, never()).delete(any());
        verify(annotations, never()).update(any());
    }

    @Test
    void U11_placementCacheNeverCrossesTargetRevisionAndLegacyIsExplicit() {
        save();
        var original = create.execute(1L, 2L, null, request(anchor));
        when(placements.find(9, 7, 1, 1)).thenReturn(Optional.of(original.placement()));
        assertEquals(
                original.placement(), query.viewCurrent(original.annotation()).placement());
        post.updateContent("title", "新内容");
        when(annotations.findVisibleByPostId(1L, 2L, null)).thenReturn(java.util.List.of(original.annotation()));
        var after = query.views(1L, 2L, null);
        assertEquals(8, after.getFirst().placement().targetRevision());
        assertEquals(PlacementStatus.UNRESOLVED, after.getFirst().placement().status());
        var legacy = new PostAnnotation(
                11L,
                1L,
                2L,
                null,
                "历史引用",
                "保留的评论",
                "yellow",
                AnnotationVisibility.PRIVATE,
                0,
                4,
                LocalDateTime.now(),
                true,
                null,
                7L);
        var legacyView = query.viewCurrent(legacy);
        assertEquals(PlacementStatus.LEGACY_UNVERIFIED, legacyView.placement().status());
        assertEquals("历史引用", legacyView.annotation().selectedText());
        verify(annotations, never()).update(any());
        verify(annotations, never()).delete(any());
    }

    @Test
    void U35_missingReplayRowNeverRecreatesAndUnavailableHashFailsClosed() {
        save();
        create.execute(1L, 2L, null, request(anchor));
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        var scope = new AnnotationRequestPort.Scope(1, "user:2", anchor.requestId());
        verify(requests).insert(eq(scope), hash.capture(), eq(9L));
        when(requests.find(scope))
                .thenReturn(Optional.of(new AnnotationRequestPort.Entry(scope, hash.getValue(), 9, false)));
        assertEquals(
                AnnotationWriteConflictException.Kind.DELETED_REQUEST,
                assertThrows(
                                AnnotationWriteConflictException.class,
                                () -> create.execute(1L, 2L, null, request(anchor)))
                        .kind());
        try (var algorithms = mockStatic(java.security.MessageDigest.class)) {
            algorithms
                    .when(() -> java.security.MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new java.security.NoSuchAlgorithmException());
            assertThrows(IllegalStateException.class, () -> create.execute(1L, 2L, null, request(anchor)));
        }
        verify(annotations, times(1)).save(any());
    }
}
