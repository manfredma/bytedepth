package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.app.project.ListProjectsQryExe;
import manfred.bytedepth.app.project.ProjectDTO;
import manfred.bytedepth.adapter.web.util.MarkdownExcerpt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ListPostsQryExe listPostsQryExe;

    @MockBean
    private ListProjectsQryExe listProjectsQryExe;

    @MockBean
    private ListCategoriesQryExe listCategoriesQryExe;

    @MockBean(name = "markdownExcerpt")
    private MarkdownExcerpt markdownExcerpt;

    @Test
    void home_returnsOkWithCorrectView() throws Exception {
        when(listPostsQryExe.execute(anyInt(), anyInt())).thenReturn(List.of());
        when(listPostsQryExe.countPublished()).thenReturn(0L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/index"));
    }

    @Test
    void home_modelContainsPostsAttribute() throws Exception {
        when(listPostsQryExe.execute(anyInt(), anyInt())).thenReturn(List.of());
        when(listPostsQryExe.countPublished()).thenReturn(0L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("posts"));
    }

    @Test
    void home_modelContainsProjectsAttribute() throws Exception {
        when(listPostsQryExe.execute(anyInt(), anyInt())).thenReturn(List.of());
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

        when(listPostsQryExe.execute(anyInt(), anyInt())).thenReturn(List.of(post));
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

        when(listPostsQryExe.execute(anyInt(), anyInt())).thenReturn(List.of());
        when(listPostsQryExe.countPublished()).thenReturn(0L);
        when(listProjectsQryExe.execute()).thenReturn(List.of(project));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("projects"));
    }

    @Test
    void home_withHotSort_exposesHotPostsAndRecentPosts() throws Exception {
        PostDTO hotPost = new PostDTO();
        hotPost.setId(1L);
        PostDTO recentPost = new PostDTO();
        recentPost.setId(2L);
        when(listPostsQryExe.executeByHotness(2, 10)).thenReturn(List.of(hotPost));
        when(listPostsQryExe.executeLatestExcluding(List.of(1L), 3)).thenReturn(List.of(recentPost));
        when(listPostsQryExe.countPublished()).thenReturn(1L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/").param("sort", "hot").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "hot"))
                .andExpect(model().attribute("paginationBaseUrl", "/?sort=hot&"))
                .andExpect(model().attributeExists("recentPosts"));

        verify(listPostsQryExe).executeByHotness(2, 10);
        verify(listPostsQryExe).executeLatestExcluding(List.of(1L), 3);
    }

    @Test
    void home_withoutSort_usesHotPostsAndRecentPosts() throws Exception {
        when(listPostsQryExe.executeByHotness(1, 10)).thenReturn(List.of());
        when(listPostsQryExe.executeLatestExcluding(List.of(), 3)).thenReturn(List.of());
        when(listPostsQryExe.countPublished()).thenReturn(0L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("sort", "hot"))
                .andExpect(model().attribute("paginationBaseUrl", "/?sort=hot&"))
                .andExpect(model().attributeExists("recentPosts"));

        verify(listPostsQryExe).executeByHotness(1, 10);
        verify(listPostsQryExe).executeLatestExcluding(List.of(), 3);
        verify(listPostsQryExe, never()).execute(anyInt(), anyInt());
    }

    @Test
    void hotSort_rendersTemplateWithSortControls() throws Exception {
        PostDTO hotPost = new PostDTO();
        hotPost.setId(1L);
        hotPost.setSlug("hot-post");
        hotPost.setTitle("热门文章");
        hotPost.setViewCount(123L);
        PostDTO recentPost = new PostDTO();
        recentPost.setId(2L);
        recentPost.setSlug("recent-post");
        recentPost.setTitle("补充文章");

        when(listPostsQryExe.executeByHotness(1, 10)).thenReturn(List.of(hotPost));
        when(listPostsQryExe.executeLatestExcluding(List.of(1L), 3)).thenReturn(List.of(recentPost));
        when(listPostsQryExe.countPublished()).thenReturn(1L);
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/").param("sort", "hot"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("最新发布")))
                .andExpect(content().string(containsString("热门文章")))
                .andExpect(content().string(containsString("123 次阅读")))
                .andExpect(content().string(containsString("/?sort=latest")))
                .andExpect(content().string(containsString("/?sort=hot")));

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
}
