package manfred.bytedepth.adapter.web.portal;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.app.rating.GetPostRatingQryExe;
import manfred.bytedepth.app.rating.PostRatingDTO;
import manfred.bytedepth.app.series.GetSeriesPostsQryExe;
import manfred.bytedepth.app.series.SeriesPostItemDTO;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import manfred.bytedepth.app.annotation.ListAnnotationsQryExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.stats.PostViewCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = PostController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class})
@Import(MarkdownRenderer.class)
class PostControllerSeriesDetailRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ListPostsQryExe listPostsQryExe;
    @MockitoBean private GetPostQryExe getPostQryExe;
    @MockitoBean
    private ListAnnotationsQryExe listAnnotationsQryExe;
    @MockitoBean private CreatePostCmdExe createPostCmdExe;
    @MockitoBean private PublishPostCmdExe publishPostCmdExe;
    @MockitoBean private ListCommentsQryExe listCommentsQryExe;
    @MockitoBean private ListTagsQryExe listTagsQryExe;
    @MockitoBean private ListCategoriesQryExe listCategoriesQryExe;
    @MockitoBean private PostViewCounter postViewCounter;
    @MockitoBean private PostRepository postRepository;
    @MockitoBean private SeriesRepository seriesRepository;
    @MockitoBean private GetSeriesPostsQryExe getSeriesPostsQryExe;
    @MockitoBean private GetPostRatingQryExe getPostRatingQryExe;
    @MockitoBean private VisitRequestFilter visitRequestFilter;

    @BeforeEach
    void setUp() {
        when(getPostRatingQryExe.execute(anyLong(), any())).thenReturn(new PostRatingDTO(0D, 0L, null));
        when(visitRequestFilter.shouldRecord(any())).thenReturn(false);
    }

    @Test
    void publishedHistoricalSeriesPostWithStandardImageRendersSuccessfully() throws Exception {
        PostDTO dto = new PostDTO();
        dto.setId(77L);
        dto.setSlug("w-tinylfu-caffeine");
        dto.setTitle("W-TinyLFU 与 Caffeine");
        dto.setContent("历史图：\\n\\n![缓存图](/images/cache.png)");
        dto.setStatus("PUBLISHED");
        dto.setPublishedAt(LocalDateTime.of(2026, 6, 2, 2, 27));
        dto.setUpdatedAt(LocalDateTime.of(2026, 6, 2, 2, 27));
        when(getPostQryExe.executeBySlug(dto.getSlug())).thenReturn(dto);

        Post post = Post.reconstruct(77L, dto.getSlug(), dto.getTitle(), dto.getContent(), PostStatus.PUBLISHED,
                dto.getPublishedAt(), dto.getPublishedAt(), dto.getUpdatedAt(), null, 1L, false);
        post.assignSeries(13L, 5);
        when(postRepository.findById(77L)).thenReturn(Optional.of(post));
        when(postRepository.findPrevPublished(77L)).thenReturn(Optional.empty());
        when(postRepository.findNextPublished(77L)).thenReturn(Optional.empty());
        when(seriesRepository.findById(13L)).thenReturn(Optional.of(Series.reconstruct(13L,
                "缓存算法", "cache-algorithms", null, 1L)));
        SeriesPostItemDTO item = new SeriesPostItemDTO();
        item.setId(77L);
        item.setSlug(dto.getSlug());
        item.setTitle(dto.getTitle());
        item.setSeriesOrder(5);
        when(getSeriesPostsQryExe.execute(13L)).thenReturn(List.of(item));
        when(listTagsQryExe.findByPostId(77L)).thenReturn(List.of());
        when(listCommentsQryExe.findApprovedByPostId(77L)).thenReturn(List.of());
        when(postViewCounter.getCount(77L)).thenReturn(0L);

        mockMvc.perform(get("/posts/w-tinylfu-caffeine"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/posts/detail"))
                .andExpect(content().string(containsString("专栏文章")))
                .andExpect(content().string(containsString("/images/cache.png")));
    }
}
