package manfred.bytedepth.adapter.web.portal;

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
import manfred.bytedepth.app.tag.ListTagsQryExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.stats.PostViewCounter;
import manfred.bytedepth.domain.stats.PostViewedEvent;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = PostController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class})
@Import(ThymeleafSecurityHandlerConfig.class)
@RecordApplicationEvents
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private ListPostsQryExe listPostsQryExe;

    @MockitoBean
    private GetPostQryExe getPostQryExe;

    @MockitoBean
    private CreatePostCmdExe createPostCmdExe;

    @MockitoBean
    private PublishPostCmdExe publishPostCmdExe;

    @MockitoBean
    private MarkdownRenderer markdownRenderer;

    @MockitoBean
    private ListCommentsQryExe listCommentsQryExe;

    @MockitoBean
    private ListTagsQryExe listTagsQryExe;

    @MockitoBean
    private PostViewCounter postViewCounter;

    @MockitoBean
    private ListCategoriesQryExe listCategoriesQryExe;

    @MockitoBean
    private PostRepository postRepository;

    @MockitoBean
    private SeriesRepository seriesRepository;

    @MockitoBean
    private GetSeriesPostsQryExe getSeriesPostsQryExe;

    @MockitoBean
    private GetPostRatingQryExe getPostRatingQryExe;

    @MockitoBean
    private VisitRequestFilter visitRequestFilter;

    @BeforeEach
    void setUp() {
        when(getPostRatingQryExe.execute(anyLong(), any())).thenReturn(new PostRatingDTO(0D, 0L, null));
        when(visitRequestFilter.shouldRecord(any())).thenReturn(true);
    }

    @Test
    void listPosts_defaultParams_returnsOkWithPostsModel() throws Exception {
        when(listPostsQryExe.execute(anyInt(), anyInt())).thenReturn(List.of());
        when(listTagsQryExe.findAllWithCount()).thenReturn(List.of());

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/posts/list"))
                .andExpect(model().attributeExists("posts"))
                .andExpect(model().attributeExists("currentPage"))
                .andExpect(model().attributeExists("allTags"));
    }

    @Test
    void listPosts_withTagParam_callsExecuteByTag() throws Exception {
        when(listPostsQryExe.executeByTag("java", 1, 10)).thenReturn(List.of());
        when(listTagsQryExe.findAllWithCount()).thenReturn(List.of());

        mockMvc.perform(get("/posts").param("tag", "java"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/posts/list"))
                .andExpect(model().attributeExists("posts"))
                .andExpect(model().attribute("activeTag", "java"));
    }

    @Test
    void listPosts_withPageAndSize_returnsOk() throws Exception {
        when(listPostsQryExe.execute(2, 5)).thenReturn(List.of());
        when(listTagsQryExe.findAllWithCount()).thenReturn(List.of());

        mockMvc.perform(get("/posts").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/posts/list"))
                .andExpect(model().attribute("currentPage", 2));
    }

    @Test
    void getPostDetail_existingPost_returnsOkWithPostModel() throws Exception {
        PostDTO dto = new PostDTO();
        dto.setId(1L);
        dto.setSlug("test-post");
        dto.setTitle("测试标题");
        dto.setContent("# 标题\n正文内容");
        dto.setStatus("PUBLISHED");

        Post domainPost = Post.reconstruct(1L, "test-post", "测试标题", "# 标题\n正文内容",
                PostStatus.PUBLISHED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false);
        when(postRepository.findById(1L)).thenReturn(Optional.of(domainPost));
        when(postRepository.findPrevPublished(1L)).thenReturn(Optional.empty());
        when(postRepository.findNextPublished(1L)).thenReturn(Optional.empty());
        when(getPostQryExe.executeBySlug("test-post")).thenReturn(dto);
        when(listTagsQryExe.findByPostId(1L)).thenReturn(List.of());
        when(listCommentsQryExe.findApprovedByPostId(1L)).thenReturn(List.of());
        when(markdownRenderer.render(dto.getContent())).thenReturn("<h1>标题</h1><p>正文内容</p>");
        when(markdownRenderer.countVisibleCharacters(dto.getContent())).thenReturn(6);
        when(postViewCounter.getCount(1L)).thenReturn(42L);

        mockMvc.perform(get("/posts/test-post"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/posts/detail"))
                .andExpect(model().attributeExists("post"))
                .andExpect(model().attributeExists("renderedContent"))
                .andExpect(model().attribute("wordCount", 6))
                .andExpect(model().attributeExists("tags"))
                .andExpect(model().attributeExists("comments"))
                .andExpect(model().attributeExists("pvCount"))
                .andExpect(content().string(containsString("id=\"post-rating\"")))
                .andExpect(content().string(not(containsString("post-rating-top"))))
                .andExpect(content().string(not(containsString("post-rating-end"))));
    }

    @Test
    void getPostDetail_numericId_redirectsToSlug() throws Exception {
        Post domainPost = Post.reconstruct(1L, "test-post", "测试标题", "内容",
                PostStatus.PUBLISHED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false);
        when(postRepository.findById(1L)).thenReturn(Optional.of(domainPost));

        mockMvc.perform(get("/posts/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String loc = result.getResponse().getRedirectedUrl();
                    assert loc != null && loc.endsWith("/posts/test-post")
                        : "Expected redirect to /posts/test-post, got: " + loc;
                });
    }

    @Test
    void getPostDetail_checksViewCountIncremented() throws Exception {
        PostDTO dto = new PostDTO();
        dto.setId(2L);
        dto.setSlug("another-post");
        dto.setTitle("另一篇文章");
        dto.setContent("内容");
        dto.setStatus("PUBLISHED");

        Post domainPost2 = Post.reconstruct(2L, "another-post", "另一篇文章", "内容",
                PostStatus.PUBLISHED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false);
        when(postRepository.findById(2L)).thenReturn(Optional.of(domainPost2));
        when(postRepository.findPrevPublished(2L)).thenReturn(Optional.empty());
        when(postRepository.findNextPublished(2L)).thenReturn(Optional.empty());
        when(getPostQryExe.executeBySlug("another-post")).thenReturn(dto);
        when(listTagsQryExe.findByPostId(anyLong())).thenReturn(List.of());
        when(listCommentsQryExe.findApprovedByPostId(anyLong())).thenReturn(List.of());
        when(markdownRenderer.render("内容")).thenReturn("<p>内容</p>");
        when(postViewCounter.getCount(2L)).thenReturn(10L);

        mockMvc.perform(get("/posts/another-post"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pvCount", 10L));
    }

    @Test
    void getPostDetail_publishesPostViewedEvent() throws Exception {
        PostDTO dto = new PostDTO();
        dto.setId(3L);
        dto.setSlug("event-test");
        dto.setTitle("事件测试");
        dto.setContent("内容");
        dto.setStatus("PUBLISHED");

        Post domainPost = Post.reconstruct(3L, "event-test", "事件测试", "内容",
                PostStatus.PUBLISHED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false);
        when(postRepository.findById(3L)).thenReturn(Optional.of(domainPost));
        when(postRepository.findPrevPublished(3L)).thenReturn(Optional.empty());
        when(postRepository.findNextPublished(3L)).thenReturn(Optional.empty());
        when(getPostQryExe.executeBySlug("event-test")).thenReturn(dto);
        when(listTagsQryExe.findByPostId(3L)).thenReturn(List.of());
        when(listCommentsQryExe.findApprovedByPostId(3L)).thenReturn(List.of());
        when(markdownRenderer.render("内容")).thenReturn("<p>内容</p>");

        mockMvc.perform(get("/posts/event-test"))
                .andExpect(status().isOk());

        assertThat(applicationEvents.stream(PostViewedEvent.class).count())
                .isGreaterThanOrEqualTo(1);
        assertThat(applicationEvents.stream(PostViewedEvent.class).findFirst().orElseThrow().visitToken())
                .isNotBlank();
    }

    @Test
    void getPostDetail_invalidVisitorDoesNotIncrementViewCountOrPublishEvent() throws Exception {
        PostDTO dto = new PostDTO();
        dto.setId(4L);
        dto.setSlug("filtered-visitor");
        dto.setTitle("过滤访问");
        dto.setContent("内容");
        dto.setStatus("PUBLISHED");

        Post domainPost = Post.reconstruct(4L, "filtered-visitor", "过滤访问", "内容",
                PostStatus.PUBLISHED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false);
        when(postRepository.findById(4L)).thenReturn(Optional.of(domainPost));
        when(postRepository.findPrevPublished(4L)).thenReturn(Optional.empty());
        when(postRepository.findNextPublished(4L)).thenReturn(Optional.empty());
        when(getPostQryExe.executeBySlug("filtered-visitor")).thenReturn(dto);
        when(listTagsQryExe.findByPostId(4L)).thenReturn(List.of());
        when(listCommentsQryExe.findApprovedByPostId(4L)).thenReturn(List.of());
        when(markdownRenderer.render("内容")).thenReturn("<p>内容</p>");
        when(visitRequestFilter.shouldRecord(any())).thenReturn(false);

        mockMvc.perform(get("/posts/filtered-visitor").header("User-Agent", "Python-urllib/3.12"))
                .andExpect(status().isOk());

        verify(postViewCounter, never()).increment(4L);
        assertThat(applicationEvents.stream(PostViewedEvent.class).count()).isZero();
    }
}
