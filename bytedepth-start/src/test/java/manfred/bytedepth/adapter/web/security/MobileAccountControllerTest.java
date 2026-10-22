package manfred.bytedepth.adapter.web.security;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import manfred.bytedepth.app.user.RegisterUserCmdExe;
import manfred.bytedepth.app.user.UsernameTakenException;
import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;

class MobileAccountControllerTest extends MobileSessionLifecycleTest {
    @Test
    void registrationsReturnFixedPending202WithoutLogin() throws Exception {
        var csrf = csrf(null);
        mvc.perform(post("/api/mobile/v1/registrations")
                        .header("X-App-Session", csrf.id())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType("application/json")
                        .content("{\"username\":\"reader\",\"password\":\"Password1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(context.getBean(RegisterUserCmdExe.class)).execute("reader", "Password1");
        mvc.perform(get("/api/mobile/v1/me").header("X-App-Session", csrf.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateAndValidationErrorsHaveIndependentJsonCodes() throws Exception {
        var register = context.getBean(RegisterUserCmdExe.class);
        doThrow(new UsernameTakenException())
                .doThrow(new DomainException("private validation detail"))
                .when(register)
                .execute("reader", "Password1");
        var csrf = csrf(null);
        for (var expected :
                java.util.List.of(new Object[] {409, "USERNAME_TAKEN"}, new Object[] {400, "INVALID_REQUEST"})) {
            mvc.perform(post("/api/mobile/v1/registrations")
                            .header("X-App-Session", csrf.id())
                            .header("X-CSRF-TOKEN", csrf.token())
                            .contentType("application/json")
                            .content("{\"username\":\"reader\",\"password\":\"Password1\"}"))
                    .andExpect(status().is((Integer) expected[0]))
                    .andExpect(jsonPath("$.code").value(expected[1]));
        }
        mvc.perform(post("/api/mobile/v1/registrations")
                        .header("X-App-Session", csrf.id())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
