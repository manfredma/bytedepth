package manfred.bytedepth.adapter.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.app.ratelimit.RateLimitDecision;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class MobileSessionLifecycleTest {
    protected AnnotationConfigWebApplicationContext context;
    protected MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Fixture.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(SessionRepositoryFilter.class))
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void loginRotatesSessionAndCsrf() throws Exception {
        var csrf = csrf(null);
        assertThat(csrf.id()).isNotBlank();
        var login = login(csrf);
        var id = login.getResponse().getHeader("X-App-Session");
        assertThat(id).isNotBlank().isNotEqualTo(csrf.id());
        assertThat(login.getResponse().getHeader("Set-Cookie")).isNull();
        mvc.perform(get("/api/mobile/v1/me").header("X-App-Session", csrf.id())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/mobile/v1/me").header("X-App-Session", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("9007199254740993"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/mobile/v1/logout").header("X-App-Session", id).header("X-CSRF-TOKEN", csrf.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var current = csrf(id);
        mvc.perform(post("/api/mobile/v1/logout").header("X-App-Session", id).header("X-CSRF-TOKEN", current.token()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-App-Session", ""));
        mvc.perform(get("/api/mobile/v1/me").header("X-App-Session", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void cookieCannotAuthenticateMobile() throws Exception {
        var web = webLogin();
        mvc.perform(get("/api/mobile/v1/me").cookie(web))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().doesNotExist("Location"));
        var mobile = login(csrf(null)).getResponse().getHeader("X-App-Session");
        mvc.perform(get("/api/mobile/v1/me").cookie(web).header("X-App-Session", mobile))
                .andExpect(status().isOk());
    }

    @Test
    void headerCannotAuthenticateWeb() throws Exception {
        var mobile = login(csrf(null)).getResponse().getHeader("X-App-Session");
        mvc.perform(get("/posts/new").header("X-App-Session", mobile))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void logoutExpiresMobileOnly() throws Exception {
        var web = webLogin();
        var id = login(csrf(null)).getResponse().getHeader("X-App-Session");
        var current = csrf(id);
        mvc.perform(post("/api/mobile/v1/logout")
                        .header("X-App-Session", id)
                        .header("X-CSRF-TOKEN", current.token())
                        .cookie(web, new jakarta.servlet.http.Cookie("bytedepth-remember-me", "web-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-App-Session", ""))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/mobile/v1/me").header("X-App-Session", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/test/web/csrf").cookie(web))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/posts/new").cookie(web)).andExpect(status().isNoContent());
    }

    @Test
    void deletedSessionImmediatelyStopsAuthenticating() throws Exception {
        var id = login(csrf(null)).getResponse().getHeader("X-App-Session");
        context.getBean(MapSessionRepository.class).deleteById(id);
        mvc.perform(get("/api/mobile/v1/me").header("X-App-Session", id)).andExpect(status().isUnauthorized());
    }

    protected jakarta.servlet.http.Cookie webLogin() throws Exception {
        var result =
                mvc.perform(get("/test/web/csrf")).andExpect(status().isOk()).andReturn();
        var token = tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(result.getResponse().getContentAsString())
                .get("token")
                .asString();
        return mvc.perform(post("/login")
                        .cookie(result.getResponse().getCookie("SESSION"))
                        .header("X-CSRF-TOKEN", token)
                        .param("username", "reader")
                        .param("password", "Password1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getResponse()
                .getCookie("SESSION");
    }

    protected Csrf csrf(String id) throws Exception {
        var request = get("/api/mobile/v1/csrf");
        if (id != null) request.header("X-App-Session", id);
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var json = tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(result.getResponse().getContentAsString());
        return new Csrf(
                id == null ? result.getResponse().getHeader("X-App-Session") : id,
                json.get("token").asString());
    }

    protected MvcResult login(Csrf csrf) throws Exception {
        return mvc.perform(post("/api/mobile/v1/login")
                        .header("X-App-Session", csrf.id())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .param("username", "reader")
                        .param("password", "Password1"))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    record Csrf(String id, String token) {}

    @Configuration
    @EnableWebMvc
    @EnableSpringHttpSession
    @Import({
        SecurityConfig.class,
        MobileSecurityConfig.class,
        SessionIdConfiguration.class,
        manfred.bytedepth.adapter.web.mobile.MobileAccountController.class,
        manfred.bytedepth.adapter.web.mobile.MobileExceptionHandler.class,
        Probe.class
    })
    static class Fixture {
        @Bean
        org.springframework.session.web.http.CookieSerializer cookieSerializer() {
            return new org.springframework.session.web.http.DefaultCookieSerializer();
        }

        @Bean
        manfred.bytedepth.app.user.RegisterUserCmdExe register() {
            return org.mockito.Mockito.mock(manfred.bytedepth.app.user.RegisterUserCmdExe.class);
        }

        @Bean
        MapSessionRepository sessionRepository() {
            return new MapSessionRepository(new ConcurrentHashMap<>());
        }

        @Bean
        UserDetailsService userDetailsService() {
            var hash = new BCryptPasswordEncoder().encode("Password1");
            return name -> new SiteUserDetails(
                    9007199254740993L,
                    name,
                    hash,
                    name.equals("limited") ? List.of() : List.of(new SimpleGrantedAuthority("blog:comment:create")));
        }

        @Bean
        RateLimitProperties rateLimitProperties() {
            return new RateLimitProperties();
        }

        @Bean
        RateLimitPort rateLimitPort() {
            var limiter = org.mockito.Mockito.mock(RateLimitPort.class);
            org.mockito.Mockito.when(limiter.tryConsume(
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any()))
                    .thenReturn(RateLimitDecision.permit());
            return limiter;
        }
    }

    @RestController
    static class Probe {
        @GetMapping("/test/web/csrf")
        Map<String, String> csrf(CsrfToken csrf) {
            return Map.of("headerName", csrf.getHeaderName(), "token", csrf.getToken());
        }

        @GetMapping("/posts/new")
        org.springframework.http.ResponseEntity<Void> webProtected() {
            return org.springframework.http.ResponseEntity.noContent().build();
        }

        @GetMapping("/api/mobile/v10/me")
        org.springframework.http.ResponseEntity<Void> otherVersion() {
            return org.springframework.http.ResponseEntity.noContent().build();
        }

        @org.springframework.web.bind.annotation.PutMapping("/api/mobile/v1/posts/1/rating")
        org.springframework.http.ResponseEntity<Void> rating() {
            return org.springframework.http.ResponseEntity.noContent().build();
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/mobile/v1/posts/1/comments")
        org.springframework.http.ResponseEntity<Void> comment() {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
    }
}
