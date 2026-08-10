package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.series.SetPostSeriesCmdExe;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.security.ContentOwnershipGuard;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.app.ratelimit.RateLimitDecision;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AdminSeriesController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, SecurityMockMvcConfig.class})
class AdminSeriesControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private SetPostSeriesCmdExe setPostSeriesCmdExe;
    @MockitoBean private ContentOwnershipGuard contentOwnershipGuard;
    @MockitoBean private PersistentTokenRepository persistentTokenRepository;
    @MockitoBean private RateLimitPort rateLimitPort;
    @MockitoBean private RateLimitProperties rateLimitProperties;

    @org.junit.jupiter.api.BeforeEach
    void allowRateLimitedRequests() {
        when(rateLimitProperties.getCommentRatingIp()).thenReturn(new RateLimitProperties.Rule());
        when(rateLimitPort.tryConsume(any(), anyLong(), any(), any())).thenReturn(RateLimitDecision.permit());
        when(contentOwnershipGuard.currentUserId(any())).thenReturn(1L);
    }

    @Test
    void regularAuthor_cannotUseLegacyAutoCreateSeriesEndpoint() throws Exception {
        mockMvc.perform(post("/admin/posts/1/series").with(csrf())
                        .param("seriesSlug", "java").param("seriesOrder", "1")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("author").authorities(() -> "blog:post:create")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void administrator_canUseLegacyAutoCreateSeriesEndpoint() throws Exception {
        mockMvc.perform(post("/admin/posts/1/series").with(csrf())
                        .param("seriesSlug", "java").param("seriesName", "Java")
                        .param("seriesOrder", "1"))
                .andExpect(status().isOk());

        verify(setPostSeriesCmdExe).execute(1L, "java", "Java", 1, 1L);
    }
}
