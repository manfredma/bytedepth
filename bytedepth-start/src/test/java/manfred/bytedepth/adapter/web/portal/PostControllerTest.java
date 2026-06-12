package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.app.series.GetSeriesPostsQryExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.stats.PostViewCounter;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = PostController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class})
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListPostsQryExe listPostsQryExe;

    @MockBean
    private GetPostQryExe getPostQryExe;

    @MockBean
    private CreatePostCmdExe createPostCmdExe;

    @MockBean
    private PublishPostCmdExe publishPostCmdExe;

    @MockBean
    private MarkdownRenderer markdownRenderer;

    @MockBean
    private ListCommentsQryExe listCommentsQryExe;

    @MockBean
    private ListTagsQryExe listTagsQryExe;

    @MockBean
    private PostViewCounter postViewCounter;

    @MockBean
    private ListCategoriesQryExe listCategoriesQryExe;

    @MockBean
    private PostRepository postRepository;

    @MockBean
    private SeriesRepository seriesRepository;

    @MockBean
    private GetSeriesPostsQryExe getSeriesPostsQryExe;

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
        when(postViewCounter.getCount(1L)).thenReturn(42L);

        mockMvc.perform(get("/posts/test-post"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/posts/detail"))
                .andExpect(model().attributeExists("post"))
                .andExpect(model().attributeExists("renderedContent"))
                .andExpect(model().attributeExists("tags"))
                .andExpect(model().attributeExists("comments"))
                .andExpect(model().attributeExists("pvCount"));
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
}
