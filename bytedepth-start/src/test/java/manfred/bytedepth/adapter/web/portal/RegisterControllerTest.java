package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.app.user.RegisterUserCmdExe;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = RegisterController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(ThymeleafSecurityHandlerConfig.class)
class RegisterControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RegisterUserCmdExe registerUserCmdExe;

    @Test
    void get_returnsRegisterView() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/register"));
    }

    @Test
    void post_success_redirectsToLoginWithParam() throws Exception {
        mockMvc.perform(post("/register")
                .param("username", "alice")
                .param("password", "secret123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?registered=1"));

        verify(registerUserCmdExe).execute("alice", "secret123");
    }

    @Test
    void post_duplicateUsername_redirectsBackWithError() throws Exception {
        doThrow(new DomainException("用户名已存在：alice"))
            .when(registerUserCmdExe).execute("alice", "pass");

        mockMvc.perform(post("/register")
                .param("username", "alice")
                .param("password", "pass"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/register?error=*"));
    }
}
