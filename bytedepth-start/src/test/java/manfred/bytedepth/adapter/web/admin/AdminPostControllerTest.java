package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmd;
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
import manfred.bytedepth.adapter.web.security.ContentOwnershipGuard;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    // AdminPostController dependencies
    @MockitoBean
    private ListAllPostsQryExe listAllPostsQryExe;

    @MockitoBean
    private GetPostQryExe getPostQryExe;

    @MockitoBean
    private CreatePostCmdExe createPostCmdExe;

    @MockitoBean
    private UpdatePostCmdExe updatePostCmdExe;

    @MockitoBean
    private PublishPostCmdExe publishPostCmdExe;

    @MockitoBean
    private DeletePostCmdExe deletePostCmdExe;

    @MockitoBean
    private ListCategoriesQryExe listCategoriesQryExe;

    @MockitoBean
    private SetPostTagsCmdExe setPostTagsCmdExe;

    @MockitoBean
    private SeriesRepository seriesRepository;

    @MockitoBean
    private ContentOwnershipGuard contentOwnershipGuard;

    @BeforeEach
    void setUpOwnershipGuard() {
        when(contentOwnershipGuard.canManagePosts(any())).thenReturn(true);
        when(contentOwnershipGuard.canManageSeries(any())).thenReturn(true);
    }

    @MockitoBean
    private AppendPostToSeriesCmdExe appendPostToSeriesCmdExe;

    @MockitoBean
    private RemovePostFromSeriesCmdExe removePostFromSeriesCmdExe;

    @MockitoBean
    private PostRepository postRepository;

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
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
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
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
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
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
    void adminNewForm_withAdmin_returnsEditView() throws Exception {
        when(listCategoriesQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/admin/posts/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/edit"))
                .andExpect(model().attributeExists("cmd"))
                .andExpect(model().attributeExists("categories"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
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
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
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
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
    void adminPublish_withAdmin_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/posts/1/publish")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/posts"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
    void adminUpdate_withAdmin_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/posts/1").with(csrf())
                        .param("title", "更新后的标题")
                        .param("content", "更新后的内容")
                        .param("categoryId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/posts"));

        verify(updatePostCmdExe).execute(1L, "更新后的标题", "更新后的内容", 2L);
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
    void adminDelete_withAdmin_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/posts/1/delete")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/posts"));
    }

    @Test
    @WithMockUser(authorities = "blog:post:create")
    void regularAuthor_listContainsOnlyOwnPostsAndSeries() throws Exception {
        when(contentOwnershipGuard.canManagePosts(any())).thenReturn(false);
        when(contentOwnershipGuard.currentUserId(any())).thenReturn(7L);
        when(listAllPostsQryExe.executeByAuthor(7L, 2, 5))
                .thenReturn(new ListAllPostsQryExe.PageResult(List.of(), 0));
        when(seriesRepository.findByAuthorId(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/posts").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/posts/list"))
                .andExpect(model().attribute("total", 0L));

        verify(listAllPostsQryExe).executeByAuthor(7L, 2, 5);
        verify(seriesRepository).findByAuthorId(7L);
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
    void adminSetTags_updatesPostTags() throws Exception {
        mockMvc.perform(post("/admin/posts/1/tags").with(csrf())
                        .param("tags", "java", "spring"))
                .andExpect(status().isOk());

        verify(setPostTagsCmdExe).execute(1L, List.of("java", "spring"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
    void updateSlug_rejectsInvalidSlug() throws Exception {
        mockMvc.perform(post("/admin/posts/1/slug").with(csrf()).param("slug", "Not Valid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
    void updateSlug_rejectsSlugOwnedByAnotherPost() throws Exception {
        var conflictingPost = manfred.bytedepth.domain.post.Post.reconstruct(
                2L, "occupied", "title", "content", manfred.bytedepth.domain.post.PostStatus.DRAFT,
                null, null, null, null, 1L, false);
        when(postRepository.findBySlug("occupied")).thenReturn(java.util.Optional.of(conflictingPost));

        mockMvc.perform(post("/admin/posts/1/slug").with(csrf()).param("slug", "occupied"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
    void updateSlug_updatesUnclaimedSlug() throws Exception {
        when(postRepository.findBySlug("available")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/admin/posts/1/slug").with(csrf()).param("slug", "available"))
                .andExpect(status().isOk());

        verify(postRepository).updateSlug(1L, "available");
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage"})
    void updateSlug_allowsKeepingTheSameSlug() throws Exception {
        var samePost = manfred.bytedepth.domain.post.Post.reconstruct(
                1L, "unchanged", "title", "content", manfred.bytedepth.domain.post.PostStatus.DRAFT,
                null, null, null, null, 1L, false);
        when(postRepository.findBySlug("unchanged")).thenReturn(java.util.Optional.of(samePost));

        mockMvc.perform(post("/admin/posts/1/slug").with(csrf()).param("slug", "unchanged"))
                .andExpect(status().isOk());

        verify(postRepository).updateSlug(1L, "unchanged");
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "blog:post:manage", "blog:series:manage"})
    void assignAndRemoveSeries_changeOnlyTheSelectedPost() throws Exception {
        mockMvc.perform(post("/admin/posts/1/series/assign").with(csrf())
                        .param("seriesId", "3").param("page", "2"))
                .andExpect(redirectedUrl("/admin/posts?page=2"));
        mockMvc.perform(post("/admin/posts/1/series/remove").with(csrf()).param("page", "2"))
                .andExpect(redirectedUrl("/admin/posts?page=2"));

        verify(appendPostToSeriesCmdExe).execute(1L, 3L);
        verify(removePostFromSeriesCmdExe).execute(1L);
        verify(contentOwnershipGuard).requireSeriesOwner(any(), eq(3L));
    }

    @Test
    void create_withoutUserDetailsLeavesAuthorUnset() {
        SecurityContextHolder.clearContext();
        try {
            CreatePostCmd cmd = new CreatePostCmd();
            directController().create(cmd);
            org.assertj.core.api.Assertions.assertThat(cmd.getAuthorUsername()).isNull();

            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("not-user-details", null));
            directController().create(new CreatePostCmd());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AdminPostController directController() {
        return new AdminPostController(listAllPostsQryExe, getPostQryExe, createPostCmdExe, updatePostCmdExe,
                publishPostCmdExe, deletePostCmdExe, listCategoriesQryExe, setPostTagsCmdExe, seriesRepository,
                appendPostToSeriesCmdExe, removePostFromSeriesCmdExe, postRepository, contentOwnershipGuard);
    }
}
