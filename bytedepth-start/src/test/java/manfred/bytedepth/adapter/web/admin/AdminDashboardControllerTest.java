package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.dashboard.DashboardStatsDTO;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.app.dashboard.DashboardStatsQryExe;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = AdminDashboardController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, ThymeleafSecurityHandlerConfig.class, SecurityMockMvcConfig.class})
class AdminDashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean
    private VisitRequestFilter visitRequestFilter;
    @MockitoBean
    private RateLimitPort rateLimitPort;
    @MockitoBean
    private RateLimitProperties rateLimitProperties;
    @MockitoBean
    private PersistentTokenRepository persistentTokenRepository;
    @MockitoBean private DashboardStatsQryExe dashboardStatsQryExe;

    @Test
    void anonymousUser_cannotAccessAdminEntry() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(dashboardStatsQryExe);
    }

    @Test
    @WithMockUser(authorities = "blog:post:create")
    void regularAuthor_isRedirectedToPersonalPostsWorkspace() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/posts"));

        verifyNoInteractions(dashboardStatsQryExe);
    }

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void administrator_seesDashboardStatistics() throws Exception {
        DashboardStatsDTO stats = new DashboardStatsDTO();
        when(dashboardStatsQryExe.execute()).thenReturn(stats);

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attribute("stats", stats));

        verify(dashboardStatsQryExe).execute();
    }
}
