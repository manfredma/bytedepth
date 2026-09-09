package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.app.project.ListProjectsQryExe;
import manfred.bytedepth.app.project.ProjectDTO;
import manfred.bytedepth.adapter.web.util.MarkdownExcerpt;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = HomeController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(ThymeleafSecurityHandlerConfig.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListPostsQryExe listPostsQryExe;
    @MockitoBean
    private VisitRequestFilter visitRequestFilter;

    @MockitoBean
    private ListProjectsQryExe listProjectsQryExe;

    @MockitoBean
    private ListCategoriesQryExe listCategoriesQryExe;

    @MockitoBean(name = "markdownExcerpt")
    private MarkdownExcerpt markdownExcerpt;

    @Test
    void home_returnsOkWithCorrectView() throws Exception {
        stubEmptyDiscoveryFeed();
        when(listPostsQryExe.countPublished()).thenReturn(0L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/index"));
    }

    @Test
    void home_modelContainsPostsAttribute() throws Exception {
        stubEmptyDiscoveryFeed();
        when(listPostsQryExe.countPublished()).thenReturn(0L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("posts"));
    }

    @Test
    void home_modelContainsProjectsAttribute() throws Exception {
        stubEmptyDiscoveryFeed();
        when(listPostsQryExe.countPublished()).thenReturn(0L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("projects"));
    }

    @Test
    void home_withPosts_exposesThemInModel() throws Exception {
        PostDTO post = new PostDTO();
        post.setId(1L);
        post.setTitle("最新文章");
        post.setStatus("PUBLISHED");

        when(listPostsQryExe.executeByHotnessExcluding(anyList(), anyInt(), anyInt())).thenReturn(List.of(post));
        when(listPostsQryExe.executeLatestExcluding(anyList(), anyInt())).thenReturn(List.of());
        when(listPostsQryExe.countPublished()).thenReturn(1L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("posts"));
    }

    @Test
    void home_withProjects_exposesThemInModel() throws Exception {
        ProjectDTO project = new ProjectDTO();
        project.setId(1L);
        project.setName("ByteDepth");

        stubEmptyDiscoveryFeed();
        when(listPostsQryExe.countPublished()).thenReturn(0L);
        when(listProjectsQryExe.execute()).thenReturn(List.of(project));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("projects"));
    }

    @Test
    void home_withHotSort_exposesOnlyHotPosts() throws Exception {
        PostDTO hotPost = new PostDTO();
        hotPost.setId(1L);
        when(listPostsQryExe.executeByHotness(2, 10)).thenReturn(List.of(hotPost));
        when(listPostsQryExe.countPublished()).thenReturn(1L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/").param("sort", "hot").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "hot"))
                .andExpect(model().attribute("paginationBaseUrl", "/?sort=hot&"));

        verify(listPostsQryExe).executeByHotness(2, 10);
        verify(listPostsQryExe, never()).executeLatestExcluding(anyList(), anyInt());
    }

    @Test
    void home_withoutSort_interleavesNewPostsIntoTheDiscoveryFeed() throws Exception {
        PostDTO hot1 = post(1L, "热门一");
        PostDTO hot2 = post(2L, "热门二");
        PostDTO hot3 = post(3L, "热门三");
        PostDTO hot4 = post(4L, "热门四");
        PostDTO hot5 = post(5L, "热门五");
        PostDTO hot6 = post(6L, "热门六");
        PostDTO hot7 = post(7L, "热门七");
        PostDTO hot8 = post(8L, "热门八");
        PostDTO recent1 = post(9L, "新发布一");
        PostDTO recent2 = post(10L, "新发布二");

        when(listPostsQryExe.executeLatestExcluding(List.of(), 2)).thenReturn(List.of(recent1, recent2));
        when(listPostsQryExe.executeByHotnessExcluding(List.of(9L, 10L), 1, 10)).thenReturn(List.of(
                hot1, hot2, hot3, hot4, hot5, hot6, hot7, hot8));
        when(listPostsQryExe.countPublished()).thenReturn(10L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "discover"))
                .andExpect(model().attribute("paginationBaseUrl", "/?sort=discover&"))
                .andExpect(model().attribute("posts", List.of(
                        hot1, hot2, hot3, hot4, recent1, hot5, hot6, hot7, hot8, recent2)))
                .andExpect(model().attribute("discoveryNewPostIds", List.of(9L, 10L)));

        verify(listPostsQryExe).executeLatestExcluding(List.of(), 2);
        verify(listPostsQryExe).executeByHotnessExcluding(List.of(9L, 10L), 1, 10);
        verify(listPostsQryExe, never()).execute(anyInt(), anyInt());
    }

    @Test
    void discoveryFeed_doesNotRepeatASingleNewPostAtTheNextInsertionPoint() throws Exception {
        PostDTO hot1 = post(1L, "热门一");
        PostDTO hot2 = post(2L, "热门二");
        PostDTO hot3 = post(3L, "热门三");
        PostDTO hot4 = post(4L, "热门四");
        PostDTO hot5 = post(5L, "热门五");
        PostDTO hot6 = post(6L, "热门六");
        PostDTO hot7 = post(7L, "热门七");
        PostDTO hot8 = post(8L, "热门八");
        PostDTO recent = post(9L, "新发布");

        when(listPostsQryExe.executeLatestExcluding(List.of(), 2)).thenReturn(List.of(recent));
        when(listPostsQryExe.executeByHotnessExcluding(List.of(9L), 1, 10)).thenReturn(List.of(
                hot1, hot2, hot3, hot4, hot5, hot6, hot7, hot8));
        when(listPostsQryExe.countPublished()).thenReturn(9L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("posts", List.of(
                        hot1, hot2, hot3, hot4, recent, hot5, hot6, hot7, hot8)));
    }

    @Test
    void discoveryFeed_showsNewPostsOnlyOnTheFirstPage() throws Exception {
        PostDTO hotPost = post(1L, "第二页热门");
        PostDTO recent1 = post(9L, "新发布一");
        PostDTO recent2 = post(10L, "新发布二");

        when(listPostsQryExe.executeLatestExcluding(List.of(), 2)).thenReturn(List.of(recent1, recent2));
        when(listPostsQryExe.executeByHotnessExcluding(List.of(9L, 10L), 2, 10)).thenReturn(List.of(hotPost));
        when(listPostsQryExe.countPublished()).thenReturn(22L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("posts", List.of(hotPost)))
                .andExpect(model().attribute("discoveryNewPostIds", List.of()))
                .andExpect(model().attribute("totalPages", 2L));

        verify(listPostsQryExe).executeByHotnessExcluding(List.of(9L, 10L), 2, 10);
    }

    @Test
    void discoveryFeed_rendersNewPostsInTheSameStream() throws Exception {
        PostDTO hotPost = new PostDTO();
        hotPost.setId(1L);
        hotPost.setSlug("hot-post");
        hotPost.setTitle("热门文章");
        hotPost.setViewCount(123L);
        PostDTO recentPost = new PostDTO();
        recentPost.setId(2L);
        recentPost.setSlug("recent-post");
        recentPost.setTitle("新发布文章");

        when(listPostsQryExe.executeLatestExcluding(List.of(), 2)).thenReturn(List.of(recentPost));
        when(listPostsQryExe.executeByHotnessExcluding(List.of(2L), 1, 10)).thenReturn(List.of(hotPost));
        when(listPostsQryExe.countPublished()).thenReturn(2L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("发现文章")))
                .andExpect(content().string(containsString("新发布")))
                .andExpect(content().string(containsString("热门文章")))
                .andExpect(content().string(containsString("href=\"/\"")))
                .andExpect(content().string(containsString("/?sort=latest")))
                .andExpect(content().string(containsString("/?sort=hot")))
                .andExpect(content().string(not(containsString("最新发布"))));

        when(listPostsQryExe.execute(1, 10)).thenReturn(List.of());
        mockMvc.perform(get("/").param("sort", "latest"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginationBaseUrl", "/?sort=latest&"))
                .andExpect(content().string(not(containsString("热门文章"))));
    }

    @Test
    void latestPageIndicatesWhenAnotherPageIsAvailable() throws Exception {
        when(listPostsQryExe.execute(1, 10)).thenReturn(List.of());
        when(listPostsQryExe.countPublished()).thenReturn(11L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());
        when(listCategoriesQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/").param("sort", "latest"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasNext", true));
    }

    private PostDTO post(long id, String title) {
        PostDTO post = new PostDTO();
        post.setId(id);
        post.setTitle(title);
        post.setSlug("post-" + id);
        return post;
    }

    private void stubEmptyDiscoveryFeed() {
        when(listPostsQryExe.executeLatestExcluding(anyList(), anyInt())).thenReturn(List.of());
        when(listPostsQryExe.executeByHotnessExcluding(anyList(), anyInt(), anyInt())).thenReturn(List.of());
    }
}
