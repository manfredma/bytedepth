package manfred.bytedepth.app.interaction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import manfred.bytedepth.app.annotation.*;
import manfred.bytedepth.app.comment.SubmitCommentCmdExe;
import manfred.bytedepth.app.post.PostWriteTransaction;
import manfred.bytedepth.app.post.document.ArticleRevisionService;
import manfred.bytedepth.app.post.query.PublicPostAccess;
import manfred.bytedepth.app.rating.*;
import manfred.bytedepth.domain.annotation.*;
import manfred.bytedepth.domain.comment.*;
import manfred.bytedepth.domain.post.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MobileInteractionServiceTest {
    final PostRepository posts = mock(PostRepository.class);
    final PostWriteTransaction tx = mock(PostWriteTransaction.class);
    final PublicPostAccess access = new PublicPostAccess(tx, posts);
    final RatePostCmdExe rate = mock(RatePostCmdExe.class);
    final GetPostRatingQryExe rating = mock(GetPostRatingQryExe.class);
    final SubmitCommentCmdExe submit = mock(SubmitCommentCmdExe.class);
    final CommentRepository comments = mock(CommentRepository.class);
    final CreateAnnotationCmdExe create = mock(CreateAnnotationCmdExe.class);
    final UpdateAnnotationCmdExe update = mock(UpdateAnnotationCmdExe.class);
    final DeleteAnnotationCmdExe delete = mock(DeleteAnnotationCmdExe.class);
    final AnnotationRepositoryPort annotations = mock(AnnotationRepositoryPort.class);
    final ArticleRevisionService current = mock(ArticleRevisionService.class);
    final ListAnnotationsQryExe query = mock(ListAnnotationsQryExe.class);
    final MobileInteractionService service = new MobileInteractionService(
            access, rate, rating, submit, comments, create, update, delete, annotations, current, query);
    final Post post = mock(Post.class);
    boolean locked;
    final String visitor = "app:7d0b5345-18ee-46b4-9609-62513f6e3043";

    @BeforeEach
    void setup() {
        when(posts.findById(1L)).thenReturn(java.util.Optional.of(post));
        when(post.getStatus()).thenReturn(PostStatus.PUBLISHED);
        when(tx.withLockedPost(eq(1L), any())).thenAnswer(i -> {
            locked = true;
            try {
                java.util.function.Function<Post, Object> f = i.getArgument(1);
                return f.apply(post);
            } finally {
                locked = false;
            }
        });
    }

    @Test
    void writesReuseInstallIdentityWhileHoldingPublishedLock() {
        doAnswer(i -> {
                    assertThat(locked).isTrue();
                    return null;
                })
                .when(rate)
                .execute(any(), any(), anyInt());
        service.rate(1L, visitor, 2);
        service.rate(1L, visitor, 5);
        verify(rate).execute(1L, visitor, 2);
        verify(rate).execute(1L, visitor, 5);
    }

    @Test
    void lockedWithdrawalPreventsEveryInteraction() {
        when(post.getStatus()).thenReturn(PostStatus.DRAFT);
        assertThatThrownBy(() -> service.rate(1L, visitor, 4)).isInstanceOf(PublicPostAccess.Unavailable.class);
        verifyNoInteractions(rate);
        assertThatThrownBy(() -> service.comment(1L, "reader", "text"))
                .isInstanceOf(PublicPostAccess.Unavailable.class);
        verifyNoInteractions(submit);
        assertThatThrownBy(() -> service.create(1L, 2L, null)).isInstanceOf(PublicPostAccess.Unavailable.class);
        assertThatThrownBy(() -> service.update(1L, 3L, 2L, "note", AnnotationVisibility.PUBLIC))
                .isInstanceOf(PublicPostAccess.Unavailable.class);
        assertThatThrownBy(() -> service.delete(1L, 3L, 2L)).isInstanceOf(PublicPostAccess.Unavailable.class);
        verifyNoInteractions(create, update, delete, annotations);

        assertThatThrownBy(() -> service.annotations(1L, 2L)).isInstanceOf(PublicPostAccess.Unavailable.class);
    }

    @Test
    void rejectsInvalidInstallOrScore() {
        for (String token : new String[] {
            null, "", "7d0b5345-18ee-46b4-9609-62513f6e3043", "app:forged", "app:7d0b5345-18ee-46b4-9609-62513f6e304x"
        }) assertThatThrownBy(() -> service.rate(1L, token, 3)).isInstanceOf(IllegalArgumentException.class);
        for (int score : new int[] {0, 6})
            assertThatThrownBy(() -> service.rate(1L, visitor, score)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rate);
    }

    @Test
    void ratingReadsAllowNoInstallButNeverAcceptWebIdentity() {
        when(rating.execute(1L, null)).thenReturn(new PostRatingDTO(0, 0, null));
        assertThat(service.ratings(1L, null).ratingCount()).isZero();
        assertThatThrownBy(() -> service.ratings(1L, "web-cookie")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commentsTrimAndEnforceLimit() {
        service.comment(1L, "reader", "  hello  ");
        verify(submit).execute(1L, "reader", "hello");
        for (String text : new String[] {null, " ", "x".repeat(2001)})
            assertThatThrownBy(() -> service.comment(1L, "reader", text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvedCommentsUseDatabasePagination() {
        var row = Comment.create(1L, 2L, "reader", "approved");
        when(comments.findApprovedByPostId(1L, 2, 10)).thenReturn(java.util.List.of(row));
        when(comments.countApprovedByPostId(1L)).thenReturn(11L);
        var result = service.comments(1L, 2, 10);
        assertThat(result).isNotNull();
        assertThat(result.items()).containsExactly(row);
        assertThat(result.total()).isEqualTo(11);
        verify(comments, never()).findApprovedByPostId(1L);
    }

    @Test
    void commentPaginationRejectsInvalidOrOverflow() {
        for (int[] p : new int[][] {{0, 10}, {1, 0}, {1, 51}, {Integer.MAX_VALUE, 50}})
            assertThatThrownBy(() -> service.comments(1L, p[0], p[1])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsOnlyForSignedInAccountAndNeverAnonymousOwnerHash() {
        var req = new CreateAnnotationRequest(null, "note", "yellow", AnnotationVisibility.PRIVATE);
        var expected = mock(AnnotationView.class);
        when(create.execute(1L, 2L, null, req)).thenAnswer(i -> {
            assertThat(locked).isTrue();
            return expected;
        });
        assertThat(service.create(1L, 2L, req)).isSameAs(expected);
        assertThatThrownBy(() -> service.create(1L, null, req)).isInstanceOf(AnnotationOwnershipException.class);
    }

    @Test
    void rejectsOtherAccountMissingOrWrongPostAnnotations() {
        var a = new PostAnnotation(
                3L,
                1L,
                2L,
                null,
                "quote",
                null,
                "yellow",
                AnnotationVisibility.PRIVATE,
                0,
                5,
                java.time.LocalDateTime.now(),
                false);
        when(annotations.findById(3L)).thenReturn(java.util.Optional.of(a));
        assertThatThrownBy(() -> service.update(1L, 3L, 4L, "note", AnnotationVisibility.PUBLIC))
                .isInstanceOf(AnnotationOwnershipException.class);
        assertThatThrownBy(() -> service.delete(1L, 3L, 4L)).isInstanceOf(AnnotationOwnershipException.class);
        assertThatThrownBy(() -> service.delete(1L, 3L, null)).isInstanceOf(AnnotationOwnershipException.class);
        assertThatThrownBy(() -> service.delete(1L, 9L, 2L)).isInstanceOf(AnnotationOwnershipException.class);
        var other = new PostAnnotation(
                3L,
                9L,
                2L,
                null,
                "quote",
                null,
                "yellow",
                AnnotationVisibility.PRIVATE,
                0,
                5,
                java.time.LocalDateTime.now(),
                false);
        when(annotations.findById(3L)).thenReturn(java.util.Optional.of(other));
        assertThatThrownBy(() -> service.delete(1L, 3L, 2L)).isInstanceOf(AnnotationOwnershipException.class);
        verifyNoInteractions(update, delete);
    }

    @Test
    void updateAndListShareLockedCurrentTarget() {
        var a = new PostAnnotation(
                3L,
                1L,
                2L,
                null,
                "quote",
                null,
                "yellow",
                AnnotationVisibility.PRIVATE,
                0,
                5,
                java.time.LocalDateTime.now(),
                false);
        when(annotations.findById(3L)).thenReturn(java.util.Optional.of(a));
        var doc = new manfred.bytedepth.app.post.document.ArticleDocumentService().render("quote");
        var revision = new PostContentRevision(1, 7, "quote", doc.document(), true);
        when(current.ensureCurrent(post)).thenAnswer(i -> {
            assertThat(locked).isTrue();
            return new ArticleRevisionService.RevisionedArticle(post, revision, doc);
        });
        when(update.execute(3L, 1L, 2L, null, "changed", AnnotationVisibility.PUBLIC))
                .thenReturn(a);
        var expected = mock(AnnotationView.class);
        when(query.view(a, revision)).thenReturn(expected);
        when(query.viewsFor(revision, 2L, null)).thenReturn(java.util.List.of(expected));
        assertThat(service.update(1L, 3L, 2L, "changed", AnnotationVisibility.PUBLIC))
                .isSameAs(expected);
        var list = service.annotations(1L, 2L);
        assertThat(list.target()).isSameAs(revision);
        assertThat(list.annotations()).containsExactly(expected);
        service.delete(1L, 3L, 2L);
        verify(delete).execute(3L, 1L, 2L, null);
    }

    @Test
    void canonicalInstallIdentityReachesRatingRead() {
        when(rating.execute(1L, visitor)).thenReturn(new PostRatingDTO(4, 2, 5));
        assertThat(service.ratings(
                                1L, visitor.toUpperCase(java.util.Locale.ROOT).replace("APP:", "app:"))
                        .visitorScore())
                .isEqualTo(5);
        verify(rating).execute(1L, visitor);
    }

    @Test
    void firstTouchLegacyPatchMigratesBeforeComputingAndCachingPlacement() {
        var now = java.time.LocalDateTime.of(2026, 10, 1, 8, 0);
        var actualPost = Post.reconstruct(
                1L, "slug", "title", "quote", PostStatus.PUBLISHED, now, null, now, null, 2L, false, 7);
        var row = new java.util.concurrent.atomic.AtomicReference<>(new PostAnnotation(
                3L, 1L, 2L, null, "quote", "old note", "yellow", AnnotationVisibility.PRIVATE, 0, 5, now, false));
        var revisionRows = new java.util.HashMap<Long, PostContentRevision>();
        var placementRows = new java.util.HashMap<java.util.List<Object>, AnnotationPlacement>();
        when(annotations.findById(3L)).thenAnswer(i -> java.util.Optional.of(row.get()));
        when(annotations.findByPostId(1L)).thenAnswer(i -> java.util.List.of(row.get()));
        when(annotations.findVisibleByPostId(1L, 2L, null)).thenAnswer(i -> java.util.List.of(row.get()));
        when(annotations.update(any())).thenAnswer(i -> {
            row.set(i.getArgument(0));
            return row.get();
        });
        doAnswer(i -> {
                    var a = row.get();
                    row.set(new PostAnnotation(
                            a.id(),
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
                            a.deleted(),
                            i.getArgument(1),
                            i.getArgument(2)));
                    return null;
                })
                .when(annotations)
                .initializeAnchor(eq(3L), any(), eq(7L));
        var revisions = mock(manfred.bytedepth.app.post.PostContentRevisionPort.class);
        when(revisions.find(eq(1L), anyLong()))
                .thenAnswer(
                        i -> java.util.Optional.ofNullable(revisionRows.get(((Number) i.getArgument(1)).longValue())));
        doAnswer(i -> {
                    PostContentRevision r = i.getArgument(0);
                    revisionRows.put(r.contentVersion(), r);
                    return null;
                })
                .when(revisions)
                .insert(any());
        var placements = mock(AnnotationPlacementPort.class);
        when(placements.find(anyLong(), anyLong(), anyInt(), anyInt()))
                .thenAnswer(i -> java.util.Optional.ofNullable(placementRows.get(
                        java.util.List.of(i.getArgument(0), i.getArgument(1), i.getArgument(2), i.getArgument(3)))));
        doAnswer(i -> {
                    AnnotationPlacement p = i.getArgument(0);
                    placementRows.put(
                            java.util.List.of(
                                    p.annotationId(), p.targetRevision(), p.projectionVersion(), p.resolverVersion()),
                            p);
                    return null;
                })
                .when(placements)
                .save(any());
        PostWriteTransaction actualTx = new PostWriteTransaction() {
            public <T> T withLockedPost(Long id, java.util.function.Function<Post, T> action) {
                return action.apply(actualPost);
            }
        };
        var documents = new manfred.bytedepth.app.post.document.ArticleDocumentService();
        var actualCurrent =
                new ArticleRevisionService(actualTx, revisions, documents, annotations, new LegacyAnchorMigrator());
        var actualQuery =
                new ListAnnotationsQryExe(annotations, actualCurrent, revisions, placements, new AnnotationResolver());
        var actualService = new MobileInteractionService(
                new PublicPostAccess(actualTx, posts),
                rate,
                rating,
                submit,
                comments,
                create,
                new UpdateAnnotationCmdExe(annotations),
                delete,
                annotations,
                actualCurrent,
                actualQuery);
        var patched = actualService.update(1L, 3L, 2L, "new note", AnnotationVisibility.PUBLIC);
        var subsequent = actualService.annotations(1L, 2L).annotations().getFirst();
        var cached = placementRows.get(java.util.List.of(3L, 7L, 1, 1));
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertThat(row.get().anchor()).isNotNull(),
                () -> assertThat(patched.annotation().anchor()).isNotNull(),
                () -> assertThat(patched.placement().status()).isEqualTo(PlacementStatus.ANCHORED),
                () -> assertThat(subsequent.placement().status()).isEqualTo(PlacementStatus.ANCHORED),
                () -> assertThat(cached.status()).isEqualTo(PlacementStatus.ANCHORED),
                () -> assertThat(patched.annotation().annotationText()).isEqualTo("new note"),
                () -> assertThat(patched.annotation().visibility()).isEqualTo(AnnotationVisibility.PUBLIC));
    }
}
