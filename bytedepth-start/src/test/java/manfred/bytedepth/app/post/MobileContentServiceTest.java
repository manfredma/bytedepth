package manfred.bytedepth.app.post;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import manfred.bytedepth.app.post.document.*;
import manfred.bytedepth.app.post.query.PublicPostAccess;
import manfred.bytedepth.app.series.*;
import manfred.bytedepth.domain.category.CategoryRepository;
import manfred.bytedepth.domain.post.*;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.Test;

class MobileContentServiceTest {
    final PostRepository posts = mock(PostRepository.class);
    final CategoryRepository categories = mock(CategoryRepository.class);
    final SeriesRepository series = mock(SeriesRepository.class);
    final ArticleRevisionService revisions = mock(ArticleRevisionService.class);
    Post post = post(PostStatus.PUBLISHED, "![first](/uploads/a.png)\n\ntext\n\n![later](https://example.org/b.png)");
    boolean locked;
    final PostWriteTransaction transactions = new PostWriteTransaction() {
        public <T> T withLockedPost(Long id, Function<Post, T> action) {
            locked = true;
            try {
                return action.apply(post);
            } finally {
                locked = false;
            }
        }
    };
    final PublicPostAccess access = new PublicPostAccess(transactions, posts);
    final MobileContentService service = new MobileContentService(
            access,
            revisions,
            categories,
            series,
            new GetSeriesPostsQryExe(series),
            new SeriesNavigationQryExe(),
            "https://bytedepth.cn");

    void prepare() {
        when(posts.findById(9007199254740993L)).thenAnswer(i -> Optional.of(post));
        when(revisions.ensureCurrent(any())).thenAnswer(i -> {
            assertThat(locked).isTrue();
            var render = new ArticleDocumentService().render(post.getContent());
            return new ArticleRevisionService.RevisionedArticle(
                    post, new PostContentRevision(post.getId(), 1, post.getContent(), render.document(), true), render);
        });
    }

    @Test
    void includesEveryImageAndStableStringIdAndUtcTime() {
        prepare();
        var content = service.content(post.getId());
        assertThat(content).isNotNull();
        assertThat(content.postId()).isEqualTo("9007199254740993");
        assertThat(content.publishedAt()).isEqualTo("2026-10-01T00:00:00Z");
        assertThat(content.resources()).hasSize(2).allSatisfy(r -> {
            assertThat(r.required()).isTrue();
            assertThat(r.role()).isEqualTo("image");
            assertThat(r.sha256()).isNull();
        });
        assertThat(content.identity()).isEqualTo(service.state(post.getId()).identity());
    }

    @Test
    void draftDeletedAndMissingExposeNoMetadata() {
        when(posts.findById(anyLong())).thenAnswer(i -> Optional.of(post));
        for (var status : List.of(PostStatus.DRAFT, PostStatus.DELETED)) {
            post = post(status, "secret");
            assertThat(service.state(post.getId())).isNotNull();
            assertThat(service.state(post.getId()).availability()).isEqualTo("UNAVAILABLE");
            assertThat(service.state(post.getId()).identity()).isNull();
            assertThatThrownBy(() -> service.content(post.getId())).isInstanceOf(NoSuchElementException.class);
        }
        when(posts.findById(anyLong())).thenReturn(Optional.empty());
        assertThat(service.state(4L).availability()).isEqualTo("UNAVAILABLE");
        verifyNoInteractions(revisions);
    }

    @Test
    void metadataChangeAltersRepresentationWithoutContentVersionChange() {
        prepare();
        var first = service.content(post.getId());
        assertThat(first).isNotNull();
        post.assignCategory(2L);
        when(categories.findById(2L))
                .thenReturn(
                        Optional.of(manfred.bytedepth.domain.category.Category.reconstruct(2L, "Java", "java", null)));
        var changed = service.content(post.getId());
        assertThat(changed.contentVersion()).isEqualTo(first.contentVersion());
        assertThat(changed.digest()).isEqualTo(first.digest());
        assertThat(changed.representationHash()).isNotEqualTo(first.representationHash());
    }

    @Test
    void resolveRechecksCurrentPublishedSlugWithinLock() {
        when(posts.findBySlug("old")).thenReturn(Optional.of(post));
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        assertThatThrownBy(() -> access.resolve("old")).isInstanceOf(NoSuchElementException.class);
        when(posts.findBySlug("slug")).thenReturn(Optional.of(post));
        assertThat(access.resolve("slug").postId()).isEqualTo("9007199254740993");
        post.delete();
        assertThatThrownBy(() -> access.resolve("slug")).isInstanceOf(NoSuchElementException.class);
    }

    static Post post(PostStatus status, String markdown) {
        var date = LocalDateTime.of(2026, 10, 1, 8, 0);
        return Post.reconstruct(
                9007199254740993L, "slug", "Title", markdown, status, date, date, date, null, 3L, false, 1);
    }

    @Test
    void relativeResourcesUseTrustedSiteOriginAndLegacyPublicationUsesCreationTime() {
        prepare();
        post = Post.reconstruct(
                post.getId(),
                "slug",
                "Title",
                "![image](/uploads/../uploads/a.png?x=1&y=2)",
                PostStatus.PUBLISHED,
                LocalDateTime.of(2026, 10, 1, 8, 0),
                null,
                null,
                null,
                null,
                false,
                1);
        var result = service.content(post.getId());
        assertThat(result.publishedAt()).isEqualTo("2026-10-01T00:00:00Z");
        assertThat(result.resources())
                .extracting(MobileContentService.Resource::url)
                .containsExactly("https://bytedepth.cn/uploads/a.png?x=1&y=2");
    }

    @Test
    void revisionFailureIsNotReportedAsTrustedWithdrawal() {
        prepare();
        doThrow(new NoSuchElementException("revision absent")).when(revisions).ensureCurrent(any());
        assertThatThrownBy(() -> service.state(post.getId()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("revision absent");
    }

    @Test
    void invalidIdsAndMissingSlugAreRejected() {
        assertThatThrownBy(() -> access.withPublishedPost(null, p -> p)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> access.withPublishedPost(0L, p -> p)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> access.resolve("missing")).isInstanceOf(NoSuchElementException.class);
        assertThat(MobileContentService.utc(null)).isNull();
    }

    @Test
    void seriesMetadataAndNavigationArePublicAndPartOfIdentity() {
        prepare();
        post.assignSeries(7L, 2);
        when(series.findById(7L))
                .thenReturn(
                        Optional.of(manfred.bytedepth.domain.series.Series.reconstruct(7L, "Column", "column", null)));
        var before = new manfred.bytedepth.domain.series.SeriesPostItem(
                1L, "before", "Before", 1, "First", "PUBLISHED", null);
        var current = new manfred.bytedepth.domain.series.SeriesPostItem(
                post.getId(), "slug", "Title", 2, "Middle", "PUBLISHED", null);
        var after = new manfred.bytedepth.domain.series.SeriesPostItem(
                9007199254740994L, "after", "After", 3, "Last", "PUBLISHED", null);
        when(series.findPublishedPostsBySeries(7L)).thenReturn(List.of(before, current, after));
        var middle = service.content(post.getId());
        assertThat(middle.series().position()).isEqualTo(2);
        assertThat(middle.series().previous().id()).isEqualTo("1");
        assertThat(middle.series().next().id()).isEqualTo("9007199254740994");
        when(series.findPublishedPostsBySeries(7L)).thenReturn(List.of(current));
        var single = service.content(post.getId());
        assertThat(single.series().previous()).isNull();
        assertThat(single.series().next()).isNull();
        assertThat(single.representationHash()).isNotEqualTo(middle.representationHash());
        when(series.findById(7L)).thenReturn(Optional.empty());
        assertThat(service.content(post.getId()).series()).isNull();
    }

    @Test
    void resourceManifestIsUniqueAndSortedAndOriginChangesIdentity() {
        prepare();
        post = post(
                PostStatus.PUBLISHED,
                "![z](z.png)\n\n![a](http://example.org/a.png)\n\n![duplicate](z.png)\n\n![unsafe](javascript:alert(1))");
        var result = service.content(post.getId());
        assertThat(result.resources())
                .extracting(MobileContentService.Resource::url)
                .containsExactly("http://example.org/a.png", "https://bytedepth.cn/z.png");
        var staging = new MobileContentService(
                access,
                revisions,
                categories,
                series,
                new GetSeriesPostsQryExe(series),
                new SeriesNavigationQryExe(),
                "https://staging-bytedepth.bytedepth.cn/");
        assertThat(staging.content(post.getId()).representationHash()).isNotEqualTo(result.representationHash());
    }

    @Test
    void getCanInitializeRevisionBaselineWithoutReadTracking() {
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        var revisionPort = mock(PostContentRevisionPort.class);
        var annotationPort = mock(manfred.bytedepth.app.annotation.AnnotationRepositoryPort.class);
        var revisionService = new ArticleRevisionService(
                transactions,
                revisionPort,
                new ArticleDocumentService(),
                annotationPort,
                mock(manfred.bytedepth.app.annotation.LegacyAnchorMigrator.class));
        var reader = new MobileContentService(
                access,
                revisionService,
                categories,
                series,
                new GetSeriesPostsQryExe(series),
                new SeriesNavigationQryExe(),
                "https://bytedepth.cn");
        assertThat(reader.content(post.getId()).html()).contains("<img");
        verify(revisionPort).insert(argThat(PostContentRevision::baseline));
    }

    @Test
    void authoredUnsafeToDownloadImageIsStillUntrustedPublicContent() {
        prepare();
        post = post(PostStatus.PUBLISHED, "Paragraph\n\n![authored](https://author:secret@example.org/image.png)");
        var result = service.content(post.getId());
        assertThat(result.html()).contains("https://author:secret&#64;example.org/image.png");
        assertThat(result.resources())
                .extracting(MobileContentService.Resource::url)
                .containsExactly("https://author:secret@example.org/image.png");
    }

    @Test
    void representationFingerprintFailureIsInternalFailure() {
        var rendered = new ArticleDocumentService().render("Text");
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(revisions.ensureCurrent(any()))
                .thenReturn(new ArticleRevisionService.RevisionedArticle(
                        post,
                        new PostContentRevision(post.getId(), 1, post.getContent(), rendered.document(), true),
                        rendered));
        try (var crypto = mockStatic(java.security.MessageDigest.class)) {
            crypto.when(() -> java.security.MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new java.security.NoSuchAlgorithmException("provider unavailable"));
            assertThatThrownBy(() -> service.content(post.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(java.security.NoSuchAlgorithmException.class);
        }
    }

    @Test
    void lockedCurrentVisibilityWinsOverPreflightSnapshotAndDecisionHoldsLock() {
        var snapshot = post;
        when(posts.findById(post.getId())).thenAnswer(invocation -> {
            post = post(PostStatus.DRAFT, "withdrawn");
            return Optional.of(snapshot);
        });
        assertThatThrownBy(() -> service.content(snapshot.getId())).isInstanceOf(PublicPostAccess.Unavailable.class);
        verifyNoInteractions(revisions);
        reset(posts);
        post = snapshot;
        prepare();
        String identity = service.withContent(post.getId(), representation -> {
            assertThat(locked).isTrue();
            return representation.postId();
        });
        assertThat(identity).isEqualTo("9007199254740993");
    }
}
