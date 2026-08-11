package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.comment.CommentDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = AdminCommentController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, ThymeleafSecurityHandlerConfig.class, SecurityMockMvcConfig.class})
class AdminCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Spring Security 所需 beans
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private VisitRequestFilter visitRequestFilter;
    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private RateLimitPort rateLimitPort;
    @MockitoBean
    private RateLimitProperties rateLimitProperties;
    @MockitoBean
    private PersistentTokenRepository persistentTokenRepository;

    // AdminCommentController 依赖
    @MockitoBean
    private ListCommentsQryExe listCommentsQryExe;

    @Test
    void adminCommentList_withoutAuth_deniesAccess() throws Exception {
        mockMvc.perform(get("/admin/comments"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void adminCommentList_withAdmin_returnsOk() throws Exception {
        when(listCommentsQryExe.findPage(anyInt(), anyInt(), any(), any()))
                .thenReturn(new ListCommentsQryExe.PageResult(List.of(), 0));

        mockMvc.perform(get("/admin/comments"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/comments/list"))
                .andExpect(model().attributeExists("comments"))
                .andExpect(model().attributeExists("filterFields"))
                .andExpect(model().attribute("filterBaseUrl", "/admin/comments?"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void adminCommentList_withAuthorNameFilter_passesFilterAndBuildsBaseUrl() throws Exception {
        CommentDTO comment = new CommentDTO();
        comment.setId(1L);
        comment.setAuthorName("alice");
        when(listCommentsQryExe.findPage(1, 50, "alice", null))
                .thenReturn(new ListCommentsQryExe.PageResult(List.of(comment), 1));

        mockMvc.perform(get("/admin/comments").param("authorName", "alice"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("total", 1L))
                .andExpect(model().attribute("filterBaseUrl", "/admin/comments?authorName=alice&"));

        verify(listCommentsQryExe).findPage(eq(1), eq(50), eq("alice"), isNull());
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void adminCommentList_withPostIdFilter_passesFilterAndBuildsBaseUrl() throws Exception {
        when(listCommentsQryExe.findPage(2, 50, null, 7L))
                .thenReturn(new ListCommentsQryExe.PageResult(List.of(), 3));

        mockMvc.perform(get("/admin/comments").param("page", "2").param("postId", "7"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalPages", 1))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("filterBaseUrl", "/admin/comments?postId=7&"));

        verify(listCommentsQryExe).findPage(eq(2), eq(50), isNull(), eq(7L));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void adminCommentList_blankAuthorNameDoesNotAddItToPaginationUrl() throws Exception {
        when(listCommentsQryExe.findPage(1, 50, " ", null)).thenReturn(new ListCommentsQryExe.PageResult(List.of(), 0));

        mockMvc.perform(get("/admin/comments").param("authorName", " "))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterBaseUrl", "/admin/comments?"));
    }
}
