package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.app.project.ListProjectsQryExe;
import manfred.bytedepth.app.project.ProjectDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void home_returnsOkWithCorrectView() throws Exception {
        when(listPostsQryExe.execute(1, 5)).thenReturn(List.of());
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/index"));
    }

    @Test
    void home_modelContainsRecentPostsAttribute() throws Exception {
        when(listPostsQryExe.execute(1, 5)).thenReturn(List.of());
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("recentPosts"));
    }

    @Test
    void home_modelContainsProjectsAttribute() throws Exception {
        when(listPostsQryExe.execute(1, 5)).thenReturn(List.of());
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("projects"));
    }

    @Test
    void home_withRecentPosts_exposesThemInModel() throws Exception {
        PostDTO post = new PostDTO();
        post.setId(1L);
        post.setTitle("最新文章");
        post.setStatus("PUBLISHED");

        when(listPostsQryExe.execute(1, 5)).thenReturn(List.of(post));
        when(listProjectsQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("recentPosts"));
    }

    @Test
    void home_withProjects_exposesThemInModel() throws Exception {
        ProjectDTO project = new ProjectDTO();
        project.setId(1L);
        project.setName("ByteDepth");

        when(listPostsQryExe.execute(anyInt(), anyInt())).thenReturn(List.of());
        when(listProjectsQryExe.execute()).thenReturn(List.of(project));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("projects"));
    }
}
