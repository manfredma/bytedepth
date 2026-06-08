package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.SetPostTagsCmdExe;
import manfred.bytedepth.app.post.command.DeletePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.command.UpdatePostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListAllPostsQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.app.series.AppendPostToSeriesCmdExe;
import manfred.bytedepth.app.series.RemovePostFromSeriesCmdExe;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = AdminPostController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
class AdminPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Spring Security requires UserDetailsService and PasswordEncoder beans
    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private PasswordEncoder passwordEncoder;

    // AdminPostController dependencies
    @MockBean
    private ListAllPostsQryExe listAllPostsQryExe;

    @MockBean
    private GetPostQryExe getPostQryExe;

    @MockBean
    private CreatePostCmdExe createPostCmdExe;

    @MockBean
    private UpdatePostCmdExe updatePostCmdExe;

    @MockBean
    private PublishPostCmdExe publishPostCmdExe;

    @MockBean
    private DeletePostCmdExe deletePostCmdExe;

    @MockBean
    private ListCategoriesQryExe listCategoriesQryExe;

    @MockBean
    private SetPostTagsCmdExe setPostTagsCmdExe;

    @MockBean
    private SeriesRepository seriesRepository;

    @MockBean
    private AppendPostToSeriesCmdExe appendPostToSeriesCmdExe;

    @MockBean
    private RemovePostFromSeriesCmdExe removePostFromSeriesCmdExe;

    // --- Authentication tests ---

    @Test
    void adminPostList_withoutAuth_deniesAccess() throws Exception {
        mockMvc.perform(get("/admin/posts"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void adminNewForm_withoutAuth_deniesAccess() throws Exception {
        mockMvc.perform(get("/admin/posts/new"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void adminEditForm_withoutAuth_deniesAccess() throws Exception {
        mockMvc.perform(get("/admin/posts/1/edit"))
                .andExpect(status().is4xxClientError());
    }

    // --- Authorized access tests ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPostList_withAdmin_returnsOk() throws Exception {
        when(listAllPostsQryExe.execute(anyInt(), anyInt()))
                .thenReturn(new ListAllPostsQryExe.PageResult(List.of(), 0));
        when(seriesRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/posts"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/list"))
                .andExpect(model().attributeExists("posts"))
                .andExpect(model().attributeExists("currentPage"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPostList_withAdmin_exposesPostsInModel() throws Exception {
        PostDTO post = new PostDTO();
        post.setId(1L);
        post.setTitle("管理后台文章");
        post.setStatus("DRAFT");

        when(listAllPostsQryExe.execute(1, 20))
                .thenReturn(new ListAllPostsQryExe.PageResult(List.of(post), 1));
        when(seriesRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/admin/posts"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("posts"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNewForm_withAdmin_returnsEditView() throws Exception {
        when(listCategoriesQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/admin/posts/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/edit"))
                .andExpect(model().attributeExists("cmd"))
                .andExpect(model().attributeExists("categories"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEditForm_withAdmin_returnsEditViewWithPost() throws Exception {
        PostDTO post = new PostDTO();
        post.setId(1L);
        post.setTitle("待编辑文章");
        post.setContent("内容");
        post.setStatus("DRAFT");

        when(getPostQryExe.execute(1L)).thenReturn(post);
        when(listCategoriesQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/admin/posts/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/edit"))
                .andExpect(model().attributeExists("post"))
                .andExpect(model().attributeExists("categories"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreate_withAdmin_redirectsToList() throws Exception {
        when(createPostCmdExe.execute(org.mockito.ArgumentMatchers.any())).thenReturn(1L);

        mockMvc.perform(post("/admin/posts")
                        .with(csrf())
                        .param("title", "新文章标题")
                        .param("content", "文章内容"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/posts"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPublish_withAdmin_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/posts/1/publish")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/posts"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDelete_withAdmin_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/posts/1/delete")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/posts"));
    }
}
