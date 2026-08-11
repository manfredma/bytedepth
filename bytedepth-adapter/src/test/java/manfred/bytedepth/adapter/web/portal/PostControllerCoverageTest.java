package manfred.bytedepth.adapter.web.portal;

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
import manfred.bytedepth.app.series.GetSeriesPostsQryExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.stats.PostViewCounter;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PostControllerCoverageTest {

    @Test
    void constructor_acceptsTheAnnotationIdentityDependency() {
        PostController controller = new PostController(mock(ListPostsQryExe.class), mock(GetPostQryExe.class),
                mock(CreatePostCmdExe.class), mock(PublishPostCmdExe.class), mock(MarkdownRenderer.class),
                mock(ListCommentsQryExe.class), mock(ListAnnotationsQryExe.class), mock(AnnotationVisitorIdentity.class),
                mock(ListTagsQryExe.class), mock(ListCategoriesQryExe.class), mock(PostViewCounter.class),
                mock(PostRepository.class), mock(SeriesRepository.class), mock(GetSeriesPostsQryExe.class),
                mock(GetPostRatingQryExe.class), mock(VisitRequestFilter.class), mock(ApplicationEventPublisher.class));

        assertThat(controller).isNotNull();
    }
}
