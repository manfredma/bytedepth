package manfred.bytedepth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

class MobileSessionIT extends MobileIntegrationFixtures {
    @Test
    void redisLoginRotatesSessionAndCsrfAndLogoutInvalidatesIt() throws Exception {
        var reader = account(false);
        var anonymous = csrf(null);
        assertThat(sessions.findById(anonymous.id())).isNotNull();
        var current = login(reader, anonymous);
        assertThat(sessions.findById(anonymous.id())).isNull();
        assertThat(sessions.findById(current.id())).isNotNull();
        perform(get(API + "/me").header("X-App-Session", current.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.toString(reader.id())))
                .andExpect(jsonPath("$.username").value(reader.username()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
        perform(request(post(API + "/logout"))
                        .header("X-App-Session", current.id())
                        .header(anonymous.header(), anonymous.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        perform(authenticated(post(API + "/logout"), current))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-App-Session", ""))
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(sessions.findById(current.id())).isNull();
        perform(get(API + "/me").header("X-App-Session", current.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void expiredAndDeletedRedisSessionsCannotAuthenticateOrReuseCsrf() throws Exception {
        var current = login(account(false));
        expireSession(current.id());
        perform(get(API + "/me").header("X-App-Session", current.id())).andExpect(status().isUnauthorized());
        perform(authenticated(post(API + "/logout"), current))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var next = login(account(false));
        sessions.deleteById(next.id());
        perform(get(API + "/me").header("X-App-Session", next.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void realWebCookieAndMobileHeaderStayIsolatedAcrossMobileLogout() throws Exception {
        var reader = account(false);
        var page = perform(request(get("/login"))).andExpect(status().isOk()).andReturn();
        var webCsrf = (CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName());
        assertThat(webCsrf).isNotNull();
        Cookie anonymous = page.getResponse().getCookie("SESSION");
        assertThat(anonymous).isNotNull();
        trackWebSession(anonymous);
        var loggedIn = perform(request(post("/login"))
                        .cookie(anonymous)
                        .header(webCsrf.getHeaderName(), webCsrf.getToken())
                        .param("username", reader.username())
                        .param("password", PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        Cookie web = loggedIn.getResponse().getCookie("SESSION");
        assertThat(web).isNotNull();
        String webId = trackWebSession(web);
        perform(get(API + "/me").cookie(web))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
        var mobile = login(reader);
        perform(get("/posts/new").header("X-App-Session", mobile.id()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
        // This account is authenticated, but has no blog:post:create permission.
        perform(get("/posts/new").cookie(web)).andExpect(status().isForbidden());
        perform(authenticated(post(API + "/logout"), mobile)
                        .cookie(web, new Cookie("bytedepth-remember-me", "unrelated-web-cookie")))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(sessions.findById(mobile.id())).isNull();
        assertThat(sessions.findById(webId)).isNotNull();
        perform(get("/posts/new").cookie(web)).andExpect(status().isForbidden());
    }

    @Test
    void registrationRequiresCsrfAndPendingAccountCannotLogin() throws Exception {
        var anonymous = csrf(null);
        String username = "mobile-it-" + java.util.UUID.randomUUID();
        String body = payload(java.util.Map.of("username", username, "password", PASSWORD));
        perform(request(post(API + "/registrations"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        try {
            perform(authenticated(post(API + "/registrations"), anonymous)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("PENDING"));
            perform(authenticated(post(API + "/login"), anonymous)
                            .param("username", username)
                            .param("password", PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
            perform(get(API + "/me").header("X-App-Session", anonymous.id())).andExpect(status().isUnauthorized());
        } finally {
            for (Long id : jdbc.queryForList("SELECT id FROM `user` WHERE username=?", Long.class, username))
                ownedAccounts.add(new Account(id, username));
        }
    }

    private String trackWebSession(Cookie cookie) {
        String id = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        ownedSessions.add(id);
        return id;
    }
}
