package manfred.bytedepth.adapter.web.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.app.user.OidcIdentityAuthenticationPort;
import manfred.bytedepth.app.user.UserAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(useDefaultFilters = false, excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@Import({
    SecurityConfig.class,
    manfred.bytedepth.adapter.web.EnvironmentAttributeAdvice.class,
    HearthOidcSecurityRoutingTest.ProtectedApi.class,
    HearthOidcSecurityConfiguration.class,
    manfred.bytedepth.adapter.web.portal.LoginController.class
})
@TestPropertySource(properties = "bytedepth.hearth-oidc.enabled=true")
class HearthOidcSecurityRoutingTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private RateLimitPort rateLimitPort;

    @MockitoBean
    private manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties rateLimitProperties;

    @MockitoBean
    private OidcIdentityAuthenticationPort oidcIdentityAuthenticationPort;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private BuildProperties buildProperties;

    @Test
    void logoutSuppressionRejectsEvenALateAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/auth/status")
                        .with(user("late-reader"))
                        .cookie(new jakarta.servlet.http.Cookie("bytedepth-auth-logout", "1")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.authenticated")
                        .value(false));
        mockMvc.perform(get("/admin")
                        .with(user("late-reader")
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "admin:dashboard:view")))
                        .cookie(new jakarta.servlet.http.Cookie("bytedepth-auth-logout", "1")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void anonymousFirstVisitCanPrepareSilentAuthenticationWithoutPriorLogin() throws Exception {
        var result = mockMvc.perform(get("/auth/status"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.authenticated")
                        .value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.suppressed")
                        .value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.csrfToken")
                        .isNotEmpty())
                .andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        org.junit.jupiter.api.Assertions.assertEquals(600, session.getMaxInactiveInterval());
        mockMvc.perform(post("/auth/silent").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.start")
                        .value("/oauth2/authorization/hearth?silent=true"));
        mockMvc.perform(post("/auth/silent").session(session).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void silentStartRequiresCsrfAndHonorsExplicitLogoutAcrossFreshSessions() throws Exception {
        mockMvc.perform(post("/auth/silent")).andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/silent")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("bytedepth-auth-logout", "1")))
                .andExpect(status().isNoContent());
    }

    @Test
    void existingAuthenticationDoesNotStartAuthorization() throws Exception {
        mockMvc.perform(post("/auth/silent").with(csrf()).with(user("reader"))).andExpect(status().isNoContent());
    }

    @Test
    void onlyTheSilentCompletionDocumentMayBeFramedByThisSite() throws Exception {
        mockMvc.perform(get("/auth/silent-complete").param("outcome", "anonymous"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(content().string(not(containsString("登录未完成"))));
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Frame-Options", "DENY"));
    }

    @Test
    void logoutReturnDoesNotImmediatelyRestartAuthorization() throws Exception {
        mockMvc.perform(get("/login").cookie(new jakarta.servlet.http.Cookie("bytedepth-auth-logout", "1")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("前往 Hearth 登录")));
    }

    @Test
    void realAuthorizationFailureReturnsQuietlyToTheSameOriginCompletion() throws Exception {
        var registrations = new HearthOidcClientConfiguration()
                .hearthClientRegistration("https://hearth.example", "bytedepth-staging", "test-only-secret");
        when(clientRegistrationRepository.findByRegistrationId("hearth"))
                .thenReturn(registrations.findByRegistrationId("hearth"));
        var session = new MockHttpSession();
        mockMvc.perform(post("/auth/silent").session(session).with(csrf())).andExpect(status().isOk());
        var start = mockMvc.perform(get("/oauth2/authorization/hearth")
                        .param("silent", "true")
                        .session(session))
                .andExpect(status().isFound())
                .andReturn();
        var params = org.springframework.web.util.UriComponentsBuilder.fromUriString(
                        start.getResponse().getRedirectedUrl())
                .build()
                .getQueryParams();
        org.junit.jupiter.api.Assertions.assertEquals("none", params.getFirst("prompt"));
        org.junit.jupiter.api.Assertions.assertEquals("S256", params.getFirst("code_challenge_method"));
        assertNotNull(params.getFirst("nonce"));
        mockMvc.perform(get("/login/oauth2/code/hearth")
                        .session(session)
                        .param("error", "login_required")
                        .param(
                                "state",
                                org.springframework.web.util.UriUtils.decode(
                                        params.getFirst("state"), java.nio.charset.StandardCharsets.UTF_8)))
                .andExpect(redirectedUrl("/auth/silent-complete?outcome=anonymous"));
        mockMvc.perform(post("/auth/silent").session(session).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void completionNormalizesUnknownOutcomesAndExplicitLoginCanOverrideLogoutHint() throws Exception {
        mockMvc.perform(get("/auth/silent-complete").param("outcome", "untrusted"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model()
                        .attribute("outcome", "anonymous"));
        mockMvc.perform(get("/login")
                        .param("interactive", "true")
                        .cookie(new jakarta.servlet.http.Cookie("bytedepth-auth-logout", "1")))
                .andExpect(redirectedUrl("/oauth2/authorization/hearth"));
        mockMvc.perform(post("/admin/users").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/users").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPageResumesOriginalEncodedQueryWithoutAdditionalParameters() throws Exception {
        var saved = mockMvc.perform(get(java.net.URI.create("/admin/users?query=a%2Bb&tag=one&tag=two")))
                .andExpect(redirectedUrl("/login?required"))
                .andReturn();
        mockMvc.perform(get("/login")
                        .param("required", "")
                        .session((MockHttpSession) saved.getRequest().getSession(false))
                        .with(user("reader")))
                .andExpect(redirectedUrl("/admin/users?query=a%2Bb&tag=one&tag=two"));
        mockMvc.perform(get("/login").param("required", "").with(user("reader")))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void normalLoginUsesHearthPageInsteadOfLocalCredentials() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/hearth"));
        mockMvc.perform(get("/admin/users")).andExpect(status().isFound()).andExpect(redirectedUrl("/login?required"));
    }

    @Test
    void protectedLandingOffersBackgroundAuthWithoutShowingAnError() throws Exception {
        mockMvc.perform(get("/login").param("required", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("auth-recovery.js")))
                .andExpect(content().string(not(containsString("登录未完成"))));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"pending", "blocked"})
    void localAccountOutcomesDoNotRestartAuthenticationOrShowPasswordForm(String outcome) throws Exception {
        mockMvc.perform(get("/login").param("oidc", outcome))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/hearth")))
                .andExpect(content().string(not(containsString("name=\"password\""))))
                .andExpect(content().string(containsString(outcome.equals("pending") ? "等待审核" : "已被封禁")));
    }

    @Test
    void failedAuthenticationShowsResultWithoutRedirectLoop() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("登录未完成")))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    void rejectedOAuthCallbackUsesFailureResultPage() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/hearth").param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void enabledHearthConfigurationBuildsTheSecurityContext() {
        assertNotNull(context.getBean("hearthFilterChain"));
    }

    @Test
    void logoutAfterLocalPrincipalConversionStillReachesHearth() throws Exception {
        var registrations = new HearthOidcClientConfiguration()
                .hearthClientRegistration("https://hearth.example", "bytedepth-staging", "test-only-secret");
        when(clientRegistrationRepository.findByRegistrationId("hearth"))
                .thenReturn(registrations.findByRegistrationId("hearth"));
        when(oidcIdentityAuthenticationPort.authenticate("https://hearth.example", "subject-1", null, null))
                .thenReturn(Optional.of(new UserAuthentication(7L, "reader", "", "ACTIVE", List.of("blog:read"))));
        var token = new OidcIdToken(
                "test-id-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("iss", "https://hearth.example", "sub", "subject-1"));
        var oidc = new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), "hearth");
        var request = new MockHttpServletRequest(context.getServletContext());
        var response = new MockHttpServletResponse();
        try {
            new HearthOidcAuthenticationSuccessHandler(
                            oidcIdentityAuthenticationPort, new HttpSessionSecurityContextRepository())
                    .onAuthenticationSuccess(request, response, oidc);
            var session = (MockHttpSession) request.getSession(false);
            assertNotNull(session);
            var result = mockMvc.perform(post("https://staging-bytedepth.bytedepth.cn:443/logout")
                            .session(session)
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            String location = result.getResponse().getRedirectedUrl();
            assertNotNull(location);
            assertTrue(location.startsWith("https://hearth.example/connect/logout?"), location);
            assertTrue(location.contains("id_token_hint=test-id-token"));
            assertTrue(java.net.URLDecoder.decode(location, java.nio.charset.StandardCharsets.UTF_8)
                    .contains("post_logout_redirect_uri=https://staging-bytedepth.bytedepth.cn/login"));
            assertTrue(session.isInvalid());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void enabledHearthLogoutStillDeletesTheLocalRememberMeCookie() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(user(new SiteUserDetails(7L, "reader", "", List.of())))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().maxAge("bytedepth-remember-me", 0));
    }

    @Test
    void anonymousLogoutReturnsHomeWithoutAnOidcIdentity() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @org.springframework.web.bind.annotation.RestController
    static class ProtectedApi {
        @org.springframework.web.bind.annotation.GetMapping("/api/test-protected")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        String read() {
            return "private";
        }
    }

    @Test
    void anonymousApiDoesNotRedirectAndMalformedSavedDestinationsAreNotFollowed() throws Exception {
        mockMvc.perform(get("/api/test-protected")).andExpect(status().isUnauthorized());
        for (String target : List.of(
                "https://localhost/private",
                "http://other.example/private",
                "http://localhost//other",
                "http://localhost/%5cprivate")) {
            var saved = org.mockito.Mockito.mock(org.springframework.security.web.savedrequest.SavedRequest.class);
            when(saved.getRedirectUrl()).thenReturn(target);
            var session = new MockHttpSession();
            session.setAttribute("SPRING_SECURITY_SAVED_REQUEST", saved);
            mockMvc.perform(get("/login").param("required", "").session(session).with(user("reader")))
                    .andExpect(redirectedUrl("/"));
        }
        var original = mockMvc.perform(get("/admin/users")).andReturn();
        mockMvc.perform(get("/login").param("required", "").with(user("reader")).session((MockHttpSession)
                        original.getRequest().getSession(false)))
                .andExpect(redirectedUrl("/admin/users"));
    }
}
