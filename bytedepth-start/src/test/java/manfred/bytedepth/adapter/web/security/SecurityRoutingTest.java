package manfred.bytedepth.adapter.web.security;

import manfred.bytedepth.adapter.web.portal.CommentController;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.app.ratelimit.RateLimitDecision;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.app.comment.SubmitCommentCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CommentController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@Import({SecurityConfig.class})
class SecurityRoutingTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean
    private VisitRequestFilter visitRequestFilter;
    @MockitoBean private SubmitCommentCmdExe submitCommentCmdExe;
    @MockitoBean private PostRepository postRepository;
    @MockitoBean private RateLimitPort rateLimitPort;
    @MockitoBean private RateLimitProperties rateLimitProperties;

    @BeforeEach
    void allowRateLimitedRequests() {
        when(rateLimitProperties.getCommentRatingIp()).thenReturn(new RateLimitProperties.Rule());
        when(rateLimitPort.tryConsume(any(), anyLong(), any(), any())).thenReturn(RateLimitDecision.permit());
    }

    @Test
    void anonymousCommentSubmission_redirectsToLoginBeforeReachingController() throws Exception {
        mockMvc.perform(post("/posts/example/comments")
                .param("content", "hello")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(submitCommentCmdExe, postRepository);
    }

    @Test
    void logout_clearsRememberMeCookieForAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/logout")
                .with(SecurityMockMvcRequestPostProcessors.user("author"))
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andExpect(result -> {
                // TokenBased remember-me 的 logout 调 cancelCookie：回写 maxAge=0 同名 cookie 通知浏览器删除。
                var cookie = result.getResponse().getCookie("bytedepth-remember-me");
                org.junit.jupiter.api.Assertions.assertNotNull(cookie);
                org.junit.jupiter.api.Assertions.assertEquals(0, cookie.getMaxAge());
            });
    }

    @Test
    void rememberMeCookie_isExplicitlyRestrictedToLaxSameSite() throws Exception {
        // 每次返回新实例：ProviderManager 默认 eraseCredentialsAfterAuthentication=true，
        // 认证成功会清空 principal(UserDetails) 的 password。TokenBased.onLoginSuccess 取不到
        // password 时会回退再调 loadUserByUsername——thenReturn 返回同一被 erase 过的实例会
        // 让 password 仍为 null，误判为无法生成 token。生产 SiteUserDetailsService 每次从
        // DB 返回新实例，回退能拿到 password hash。
        when(userDetailsService.loadUserByUsername("author"))
            .thenAnswer(inv -> User.withUsername("author").password("encoded-password").authorities("blog:post:create").build());
        when(passwordEncoder.matches("secret", "encoded-password")).thenReturn(true);

        mockMvc.perform(post("/login")
                .param("username", "author")
                .param("password", "secret")
                .param("remember-me", "on")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andExpect(result -> {
                var cookie = result.getResponse().getCookie("bytedepth-remember-me");
                org.junit.jupiter.api.Assertions.assertNotNull(cookie);
                org.junit.jupiter.api.Assertions.assertEquals("Lax", cookie.getAttribute("SameSite"));
                org.junit.jupiter.api.Assertions.assertTrue(cookie.isHttpOnly());
            });
    }

    @Test
    void accessDenied_returnsForbiddenForAuthenticatedUserWithoutPermission() throws Exception {
        mockMvc.perform(get("/admin")
                .with(SecurityMockMvcRequestPostProcessors.user("author")))
            .andExpect(status().isForbidden());
    }

}
