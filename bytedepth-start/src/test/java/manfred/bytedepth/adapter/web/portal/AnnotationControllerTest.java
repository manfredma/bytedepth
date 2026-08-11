package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.SiteUserDetails;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.annotation.CreateAnnotationCmdExe;
import manfred.bytedepth.app.annotation.DeleteAnnotationCmdExe;
import manfred.bytedepth.app.annotation.ListAnnotationsQryExe;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AnnotationController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, ThymeleafSecurityHandlerConfig.class, SecurityMockMvcConfig.class})
class AnnotationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private RateLimitPort rateLimitPort;
    @MockitoBean
    private RateLimitProperties rateLimitProperties;
    @MockitoBean
    private PersistentTokenRepository persistentTokenRepository;
    @MockitoBean
    private VisitRequestFilter visitRequestFilter;
    @MockitoBean
    private PostRepository postRepository;
    @MockitoBean
    private ListAnnotationsQryExe listAnnotationsQryExe;
    @MockitoBean
    private CreateAnnotationCmdExe createAnnotationCmdExe;
    @MockitoBean
    private DeleteAnnotationCmdExe deleteAnnotationCmdExe;

    @BeforeEach
    void setUp() {
        when(postRepository.findBySlug("test-post")).thenReturn(
                Optional.of(manfred.bytedepth.domain.post.Post.reconstruct(
                        1L, "test-post", "测试标题", "内容",
                        manfred.bytedepth.domain.post.PostStatus.PUBLISHED,
                        LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                        null, null, false)));
    }

    private static SiteUserDetails siteUser() {
        return new SiteUserDetails(42L, "alice", "pw",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static PostAnnotation dto() {
        return new PostAnnotation(10L, 1L, 42L, "被批注文本", "批注内容",
                "yellow", 0, 5, LocalDateTime.now());
    }

    // ── GET：公开 ──────────────────────────────────────────

    @Test
    void list_withoutAuth_returnsAnnotations() throws Exception {
        when(listAnnotationsQryExe.execute(1L)).thenReturn(List.of(dto()));

        mockMvc.perform(get("/posts/test-post/annotations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].selectedText").value("被批注文本"));
    }

    @Test
    void list_noAnnotations_returnsEmptyArray() throws Exception {
        when(listAnnotationsQryExe.execute(1L)).thenReturn(List.of());

        mockMvc.perform(get("/posts/test-post/annotations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void list_postNotFound_returnsNotFound() throws Exception {
        when(postRepository.findBySlug("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/posts/missing/annotations"))
                .andExpect(status().isNotFound());
    }

    // ── POST：需登录 ───────────────────────────────────────

    @Test
    void create_withoutAuth_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/posts/test-post/annotations")
                        .contentType("application/json")
                        .content("{}").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void create_withLogin_createsAndReturns() throws Exception {
        when(createAnnotationCmdExe.execute(eq(1L), eq(42L), anyString(), anyString(),
                anyString(), anyInt(), anyInt())).thenReturn(dto());

        mockMvc.perform(post("/posts/test-post/annotations")
                        .contentType("application/json")
                        .content("""
                                {"selectedText":"被批注文本","annotationText":"批注内容",
                                 "color":"yellow","startOffset":0,"endOffset":5}
                                """)
                        .with(csrf()).with(user(siteUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.userId").value(42));

        verify(createAnnotationCmdExe).execute(eq(1L), eq(42L), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void create_invalidInput_returnsBadRequest() throws Exception {
        doThrow(new DomainException("批注偏移越界")).when(createAnnotationCmdExe)
                .execute(any(), any(), any(), any(), any(), anyInt(), anyInt());

        mockMvc.perform(post("/posts/test-post/annotations")
                        .contentType("application/json")
                        .content("""
                                {"selectedText":"x","annotationText":"y",
                                 "color":"yellow","startOffset":99,"endOffset":5}
                                """)
                        .with(csrf()).with(user(siteUser())))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE：需登录且仅作者 ─────────────────────────────

    @Test
    void delete_withoutAuth_redirectsToLogin() throws Exception {
        mockMvc.perform(delete("/posts/test-post/annotations/10").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void delete_ownAnnotation_returnsNoContent() throws Exception {

        mockMvc.perform(delete("/posts/test-post/annotations/10").with(csrf()).with(user(siteUser())))
                .andExpect(status().isNoContent());

        verify(deleteAnnotationCmdExe).execute(10L, 42L);
    }

    @Test
    void delete_otherUsersAnnotation_returnsBadRequest() throws Exception {
        doThrow(new DomainException("只能删除自己的批注"))
                .when(deleteAnnotationCmdExe).execute(10L, 42L);

        mockMvc.perform(delete("/posts/test-post/annotations/10").with(csrf()).with(user(siteUser())))
                .andExpect(status().isBadRequest());
    }
}
