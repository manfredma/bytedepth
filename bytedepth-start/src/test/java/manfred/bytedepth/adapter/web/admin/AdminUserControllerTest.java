package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.user.ActivateUserCmdExe;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.app.user.BanUserCmdExe;
import manfred.bytedepth.app.user.ListPendingUsersQryExe;
import manfred.bytedepth.domain.user.UserRepository;
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

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AdminUserController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, ThymeleafSecurityHandlerConfig.class, SecurityMockMvcConfig.class})
class AdminUserControllerTest {

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
    @MockitoBean private ListPendingUsersQryExe listPendingUsersQryExe;
    @MockitoBean private ActivateUserCmdExe activateUserCmdExe;
    @MockitoBean private BanUserCmdExe banUserCmdExe;
    @MockitoBean private UserRepository userRepository;

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void list_returnsUsersView() throws Exception {
        when(listPendingUsersQryExe.findPage(null, null, 1, 20)).thenReturn(new ListPendingUsersQryExe.UserPageResult(List.of(), 0));

        mockMvc.perform(get("/admin/users"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/users/list"))
            .andExpect(model().attributeExists("users", "filterFields", "filterBaseUrl"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void list_supportsNonBlankUsernameAndEachSelectedStatus() throws Exception {
        when(listPendingUsersQryExe.findPage("alice", "ACTIVE", 1, 20)).thenReturn(new ListPendingUsersQryExe.UserPageResult(List.of(), 1));
        when(listPendingUsersQryExe.findPage(" ", "BANNED", 1, 20)).thenReturn(new ListPendingUsersQryExe.UserPageResult(List.of(), 1));

        mockMvc.perform(get("/admin/users").param("username", "alice").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterBaseUrl", "/admin/users?username=alice&status=ACTIVE&"));
        mockMvc.perform(get("/admin/users").param("username", " ").param("status", "BANNED"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterBaseUrl", "/admin/users?status=BANNED&"));
        when(listPendingUsersQryExe.findPage(null, "PENDING", 1, 20)).thenReturn(new ListPendingUsersQryExe.UserPageResult(List.of(), 1));
        mockMvc.perform(get("/admin/users").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterBaseUrl", "/admin/users?status=PENDING&"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void activate_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/users/1/activate").with(csrf()))
            .andExpect(redirectedUrl("/admin/users"));
        verify(activateUserCmdExe).execute(1L);
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void deletePending_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/users/1/delete").with(csrf()))
            .andExpect(redirectedUrl("/admin/users"));
        verify(userRepository).deleteById(1L);
    }
}
