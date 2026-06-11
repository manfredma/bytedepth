package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.user.ActivateUserCmdExe;
import manfred.bytedepth.app.user.BanUserCmdExe;
import manfred.bytedepth.app.user.ListPendingUsersQryExe;
import manfred.bytedepth.domain.user.UserRepository;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AdminUserController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
class AdminUserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private ListPendingUsersQryExe listPendingUsersQryExe;
    @MockBean private ActivateUserCmdExe activateUserCmdExe;
    @MockBean private BanUserCmdExe banUserCmdExe;
    @MockBean private UserRepository userRepository;

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view", "system:user:approve"})
    void list_returnsUsersView() throws Exception {
        when(listPendingUsersQryExe.execute()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/users/list"))
            .andExpect(model().attributeExists("pendingUsers"));
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
