package manfred.bytedepth.adapter.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import manfred.bytedepth.app.user.OidcIdentityAuthenticationPort;
import manfred.bytedepth.app.user.UserAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

class HearthOidcAuthenticationSuccessHandlerTest {

    @org.junit.jupiter.api.AfterEach
    void cleanupContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void identityMappingFailureCannotLeaveTemporaryOidcAuthenticationInSession() throws Exception {
        var identities = mock(OidcIdentityAuthenticationPort.class);
        var oidc = mock(OidcUser.class);
        when(oidc.getSubject()).thenReturn("test-subject");
        when(identities.authenticate("", "test-subject", null, null))
                .thenThrow(new IllegalStateException("database unavailable"));
        var authentication = oidcUserAuthentication(oidc);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var silent = new HearthSilentAuthentication();
        SecurityContextHolder.clearContext();
        org.junit.jupiter.api.Assertions.assertTrue(silent.begin(request));
        org.junit.jupiter.api.Assertions.assertTrue(silent.arm(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> new HearthOidcAuthenticationSuccessHandler(identities, new HttpSessionSecurityContextRepository())
                        .onAuthenticationSuccess(request, response, authentication));
        assertEquals("/auth/silent-complete?outcome=anonymous", response.getRedirectedUrl());
        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ACTIVE", "PENDING", "BANNED", "LOGGED_OUT"})
    void silentCallbackHasBoundedLocalAccountOutcomes(String status) throws Exception {
        var identities = mock(OidcIdentityAuthenticationPort.class);
        var oidc = mock(OidcUser.class);
        when(oidc.getSubject()).thenReturn("test-subject");
        when(identities.authenticate("", "test-subject", null, null))
                .thenReturn(Optional.of(new UserAuthentication(1L, "reader", "", status, List.of())));
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var silent = new HearthSilentAuthentication();
        org.junit.jupiter.api.Assertions.assertTrue(silent.begin(request));
        org.junit.jupiter.api.Assertions.assertTrue(silent.arm(request));
        if ("LOGGED_OUT".equals(status)) {
            request.setCookies(new jakarta.servlet.http.Cookie("bytedepth-auth-logout", "1"));
        }
        new HearthOidcAuthenticationSuccessHandler(identities, new HttpSessionSecurityContextRepository())
                .onAuthenticationSuccess(request, response, oidcUserAuthentication(oidc));
        var expected = switch (status) {
            case "ACTIVE" -> "success";
            case "PENDING" -> "pending";
            case "BANNED" -> "blocked";
            default -> "anonymous";
        };
        assertEquals("/auth/silent-complete?outcome=" + expected, response.getRedirectedUrl());
        assertEquals("ACTIVE".equals(status), silent.authenticated());
        if ("ACTIVE".equals(status))
            org.junit.jupiter.api.Assertions.assertNull(response.getCookie("bytedepth-auth-logout"));
    }

    @Test
    void successReplacesOidcPrincipalWithLocalUserAndPermissions() throws Exception {
        OidcIdentityAuthenticationPort identities = mock(OidcIdentityAuthenticationPort.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getIssuer())
                .thenReturn(URI.create("https://hearth.example").toURL());
        when(oidcUser.getSubject()).thenReturn("subject-1");
        when(oidcUser.getClaimAsString("preferred_username")).thenReturn("alice");
        when(oidcUser.getClaimAsString("email")).thenReturn("alice@example.com");
        when(identities.authenticate("https://hearth.example", "subject-1", "alice", "alice@example.com"))
                .thenReturn(
                        Optional.of(new UserAuthentication(7L, "alice", "", "ACTIVE", List.of("blog:post:create"))));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        SecurityContextHolder.clearContext();

        new HearthOidcAuthenticationSuccessHandler(identities, new HttpSessionSecurityContextRepository())
                .onAuthenticationSuccess(request, response, oidcUserAuthentication(oidcUser));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(SiteUserDetails.class, authentication.getPrincipal().getClass());
        assertEquals(7L, ((SiteUserDetails) authentication.getPrincipal()).getId());
        assertEquals(
                "blog:post:create",
                authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void pendingIdentityDoesNotLeaveTheTemporaryOidcSessionAuthenticated() throws Exception {
        OidcIdentityAuthenticationPort identities = mock(OidcIdentityAuthenticationPort.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getIssuer())
                .thenReturn(URI.create("https://hearth.example").toURL());
        when(oidcUser.getSubject()).thenReturn("subject-1");
        when(identities.authenticate("https://hearth.example", "subject-1", null, null))
                .thenReturn(Optional.of(new UserAuthentication(7L, "alice", "", "PENDING", List.of())));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        SecurityContextHolder.getContext().setAuthentication(oidcUserAuthentication(oidcUser));

        new HearthOidcAuthenticationSuccessHandler(identities, new HttpSessionSecurityContextRepository())
                .onAuthenticationSuccess(request, response, oidcUserAuthentication(oidcUser));

        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void bannedIdentityIsRedirectedAndCleared() throws Exception {
        OidcIdentityAuthenticationPort identities = mock(OidcIdentityAuthenticationPort.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getIssuer())
                .thenReturn(URI.create("https://hearth.example").toURL());
        when(oidcUser.getSubject()).thenReturn("subject-1");
        when(identities.authenticate("https://hearth.example", "subject-1", null, null))
                .thenReturn(Optional.of(new UserAuthentication(7L, "alice", "", "BANNED", List.of())));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        SecurityContextRepository repository = mock(SecurityContextRepository.class);
        SecurityContextHolder.getContext().setAuthentication(oidcUserAuthentication(oidcUser));

        new HearthOidcAuthenticationSuccessHandler(identities, repository)
                .onAuthenticationSuccess(request, response, oidcUserAuthentication(oidcUser));

        verify(response).sendRedirect("/login?oidc=blocked");
        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidPrincipalReturnsUnauthorized() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);

        new HearthOidcAuthenticationSuccessHandler(
                        mock(OidcIdentityAuthenticationPort.class), mock(SecurityContextRepository.class))
                .onAuthenticationSuccess(mock(HttpServletRequest.class), response, mock(Authentication.class));

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Hearth OIDC principal is invalid");

        HttpServletResponse nonOidcResponse = mock(HttpServletResponse.class);
        new HearthOidcAuthenticationSuccessHandler(
                        mock(OidcIdentityAuthenticationPort.class), mock(SecurityContextRepository.class))
                .onAuthenticationSuccess(
                        mock(HttpServletRequest.class),
                        nonOidcResponse,
                        new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                                mock(OAuth2User.class), List.of(), "hearth"));

        verify(nonOidcResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Hearth OIDC principal is invalid");
    }

    @Test
    void missingLocalIdentityFailsClosed() throws Exception {
        OidcIdentityAuthenticationPort identities = mock(OidcIdentityAuthenticationPort.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getIssuer())
                .thenReturn(URI.create("https://hearth.example").toURL());
        when(oidcUser.getSubject()).thenReturn("subject-1");
        when(identities.authenticate("https://hearth.example", "subject-1", null, null))
                .thenReturn(Optional.empty());

        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new HearthOidcAuthenticationSuccessHandler(identities, mock(SecurityContextRepository.class))
                .onAuthenticationSuccess(mock(HttpServletRequest.class), response, oidcUserAuthentication(oidcUser));
        assertEquals("/login?error", response.getRedirectedUrl());
        assertEquals(null, SecurityContextHolder.getContext().getAuthentication());
    }

    private static Authentication oidcUserAuthentication(OidcUser user) {
        return new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                user, List.of(), "hearth");
    }
}
