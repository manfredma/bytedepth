package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.dashboard.DashboardStatsDTO;
import manfred.bytedepth.app.dashboard.DashboardStatsQryExe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class AdminDashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private DashboardStatsQryExe dashboardStatsQryExe;

    @Test
    void anonymousUser_cannotAccessAdminEntry() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is4xxClientError());

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
