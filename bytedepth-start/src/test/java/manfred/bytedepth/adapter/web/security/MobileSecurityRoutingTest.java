package manfred.bytedepth.adapter.web.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

class MobileSecurityRoutingTest extends MobileSessionLifecycleTest {
    @Test
    void anonymousMeAndRememberMeCookieCannotBypassHeaderAuthentication() throws Exception {
        mvc.perform(get("/api/mobile/v1/me").cookie(new Cookie("bytedepth-remember-me", "web-only")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/api/mobile/v10/me")).andExpect(status().isNoContent());
    }

    @Test
    void allWritesKeepCsrfAndUnknownWritesAreDenied() throws Exception {
        var csrf = csrf(null);
        for (var request : java.util.List.of(
                post("/api/mobile/v1/login"),
                post("/api/mobile/v1/registrations"),
                put("/api/mobile/v1/posts/1/rating"),
                patch("/api/mobile/v1/posts/1/annotations/1"),
                delete("/api/mobile/v1/posts/1/annotations/1"))) {
            mvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }
        mvc.perform(put("/api/mobile/v1/posts/1/rating")
                        .header("X-App-Session", csrf.id())
                        .header("X-CSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/mobile/v1/posts/1/comments")
                        .header("X-App-Session", csrf.id())
                        .header("X-CSRF-TOKEN", csrf.token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/mobile/v1/unknown")
                        .header("X-App-Session", csrf.id())
                        .header("X-CSRF-TOKEN", csrf.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void failedLoginReturnsStableJsonWithoutExceptionOrRedirect() throws Exception {
        var csrf = csrf(null);
        mvc.perform(post("/api/mobile/v1/login")
                        .header("X-App-Session", csrf.id())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .param("username", "reader")
                        .param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void permissionFailuresAreForbiddenAndLoginChargesEachBucketOnce() throws Exception {
        var csrf = csrf(null);
        var limited = mvc.perform(post("/api/mobile/v1/login")
                        .header("X-App-Session", csrf.id())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .param("username", "limited")
                        .param("password", "Password1"))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse()
                .getHeader("X-App-Session");
        var current = csrf(limited);
        mvc.perform(post("/api/mobile/v1/posts/1/comments")
                        .header("X-App-Session", limited)
                        .header("X-CSRF-TOKEN", current.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        var limiter = context.getBean(manfred.bytedepth.app.ratelimit.RateLimitPort.class);
        org.mockito.Mockito.verify(limiter)
                .tryConsume(
                        org.mockito.ArgumentMatchers.eq("login-ip"),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(limiter)
                .tryConsume(
                        org.mockito.ArgumentMatchers.eq("login-username"),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("limited"));
        var id = login(csrf(null)).getResponse().getHeader("X-App-Session");
        var permitted = csrf(id);
        mvc.perform(post("/api/mobile/v1/posts/1/comments")
                        .header("X-App-Session", id)
                        .header("X-CSRF-TOKEN", permitted.token()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/mobile/v1/unknown")
                        .header("X-App-Session", id)
                        .header("X-CSRF-TOKEN", permitted.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
