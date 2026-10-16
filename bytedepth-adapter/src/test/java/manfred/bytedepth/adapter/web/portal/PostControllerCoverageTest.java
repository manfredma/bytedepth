package manfred.bytedepth.adapter.web.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.annotation.ListAnnotationsQryExe;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.rating.GetPostRatingQryExe;
import manfred.bytedepth.app.reading.GetReadingSummaryQryExe;
import manfred.bytedepth.app.series.GetSeriesPostsQryExe;
import manfred.bytedepth.app.series.SeriesNavigationQryExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.stats.PostViewCounter;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class PostControllerCoverageTest {

    @Test
    void U36_pageUsesLockedRevisionForHtmlModelTitleAndPlacements() {
        var get = mock(GetPostQryExe.class);
        var revisions = mock(manfred.bytedepth.app.post.document.ArticleRevisionService.class);
        var annotations = mock(ListAnnotationsQryExe.class);
        var posts = mock(PostRepository.class);
        var controller = new PostController(
                mock(ListPostsQryExe.class),
                get,
                revisions,
                mock(CreatePostCmdExe.class),
                mock(PublishPostCmdExe.class),
                new MarkdownRenderer(),
                mock(ListCommentsQryExe.class),
                annotations,
                mock(AnnotationVisitorIdentity.class),
                mock(ListTagsQryExe.class),
                mock(ListCategoriesQryExe.class),
                mock(PostViewCounter.class),
                posts,
                mock(SeriesRepository.class),
                mock(GetSeriesPostsQryExe.class),
                mock(SeriesNavigationQryExe.class),
                mock(GetPostRatingQryExe.class),
                mock(VisitRequestFilter.class),
                mock(ApplicationEventPublisher.class));
        var stale = new manfred.bytedepth.app.post.query.PostDTO();
        stale.setId(1L);
        stale.setTitle("旧标题");
        stale.setContent("旧内容");
        stale.setStatus("PUBLISHED");
        stale.setSlug("slug");
        org.mockito.Mockito.when(get.executeBySlug("slug")).thenReturn(stale);
        var fresh = manfred.bytedepth.domain.post.Post.reconstruct(
                1L,
                "slug",
                "新标题",
                "新的😀正文",
                manfred.bytedepth.domain.post.PostStatus.PUBLISHED,
                java.time.LocalDateTime.now(),
                null,
                java.time.LocalDateTime.now(),
                null,
                7L,
                false,
                8);
        var rendered = new manfred.bytedepth.app.post.document.ArticleDocumentService().render(fresh.getContent());
        var revision = new manfred.bytedepth.domain.post.PostContentRevision(
                1, 8, fresh.getContent(), rendered.document(), false);
        org.mockito.Mockito.when(revisions.current(1))
                .thenReturn(new manfred.bytedepth.app.post.document.ArticleRevisionService.RevisionedArticle(
                        fresh, revision, rendered));
        org.mockito.Mockito.when(posts.findById(1L)).thenReturn(java.util.Optional.of(fresh));
        var model = new org.springframework.ui.ExtendedModelMap();
        assertThat(controller.detail("slug", model, new org.springframework.mock.web.MockHttpServletRequest()))
                .isEqualTo("public/posts/detail");
        assertThat(model.get("renderedContent")).isEqualTo(rendered.html());
        var bootstrap = (PostController.AnnotationDocumentBootstrap) model.get("annotationDocument");
        assertThat(bootstrap.revision()).isEqualTo(8);
        assertThat(bootstrap.text()).isEqualTo("新的😀正文");
        assertThat(stale.getTitle()).isEqualTo("新标题");
        assertThat(stale.getContentVersion()).isEqualTo(8);
        org.mockito.Mockito.verify(annotations).viewsFor(revision, null, null);
        fresh.delete();
        org.junit.jupiter.api.Assertions.assertThrows(
                java.util.NoSuchElementException.class,
                () -> controller.detail(
                        "slug",
                        new org.springframework.ui.ExtendedModelMap(),
                        new org.springframework.mock.web.MockHttpServletRequest()));
    }

    @Test
    void constructor_acceptsTheAnnotationIdentityDependency() {
        PostController controller = new PostController(
                mock(ListPostsQryExe.class),
                mock(GetPostQryExe.class),
                mock(manfred.bytedepth.app.post.document.ArticleRevisionService.class),
                mock(CreatePostCmdExe.class),
                mock(PublishPostCmdExe.class),
                mock(MarkdownRenderer.class),
                mock(ListCommentsQryExe.class),
                mock(ListAnnotationsQryExe.class),
                mock(AnnotationVisitorIdentity.class),
                mock(ListTagsQryExe.class),
                mock(ListCategoriesQryExe.class),
                mock(PostViewCounter.class),
                mock(PostRepository.class),
                mock(SeriesRepository.class),
                mock(GetSeriesPostsQryExe.class),
                mock(SeriesNavigationQryExe.class),
                mock(GetPostRatingQryExe.class),
                mock(VisitRequestFilter.class),
                mock(ApplicationEventPublisher.class));

        assertThat(controller).isNotNull();
    }

    @Test
    void readingSummaryHelperUsesTheAuthenticatedSummaryQuery() {
        PostController controller = new PostController(
                mock(ListPostsQryExe.class),
                mock(GetPostQryExe.class),
                mock(manfred.bytedepth.app.post.document.ArticleRevisionService.class),
                mock(CreatePostCmdExe.class),
                mock(PublishPostCmdExe.class),
                mock(MarkdownRenderer.class),
                mock(ListCommentsQryExe.class),
                mock(ListAnnotationsQryExe.class),
                mock(AnnotationVisitorIdentity.class),
                mock(ListTagsQryExe.class),
                mock(ListCategoriesQryExe.class),
                mock(PostViewCounter.class),
                mock(PostRepository.class),
                mock(SeriesRepository.class),
                mock(GetSeriesPostsQryExe.class),
                mock(SeriesNavigationQryExe.class),
                mock(GetPostRatingQryExe.class),
                mock(VisitRequestFilter.class),
                mock(ApplicationEventPublisher.class));
        var query = mock(GetReadingSummaryQryExe.class);
        ReflectionTestUtils.setField(controller, "getReadingSummaryQryExe", query);
        ReflectionTestUtils.invokeMethod(controller, "readingSummary", 7L, 12L);
        org.mockito.Mockito.verify(query).execute(7L, 12L);
    }
}
