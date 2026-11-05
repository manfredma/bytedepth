package manfred.bytedepth.adapter.web.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class HearthAuthorizationRequestResolverTest {
    private final HearthAuthorizationRequestResolver resolver =
            new HearthAuthorizationRequestResolver(new HearthOidcClientConfiguration()
                    .hearthClientRegistration("https://hearth.example", "bytedepth-staging", "test-secret"));

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }

    @Test
    void onlyCsrfPreparedRequestGetsPromptNoneAndPreservesFrameworkSecurityMaterials() {
        var request = request("/oauth2/authorization/hearth");
        request.setParameter("silent", "true");
        assertNull(resolver.resolve(request));
        assertTrue(new HearthSilentAuthentication().begin(request));
        var generated = resolver.resolve(request);
        assertNotNull(generated);
        assertEquals("none", generated.getAdditionalParameters().get("prompt"));
        assertEquals("S256", generated.getAdditionalParameters().get("code_challenge_method"));
        assertNotNull(generated.getState());
        assertNotNull(generated.getAdditionalParameters().get("nonce"));
        assertNotNull(generated.getAttributes().get("code_verifier"));
        assertNull(resolver.resolve(request));
    }

    @Test
    void normalLoginGeneratesFreshMaterialsAndReplacesSilentContext() {
        var request = request("/oauth2/authorization/hearth");
        new HearthSilentAuthentication().begin(request);
        request.setParameter("silent", "true");
        var silent = resolver.resolve(request, "hearth");
        request.removeParameter("silent");
        var interactive = resolver.resolve(request);
        assertNotNull(interactive);
        assertFalse(interactive.getAdditionalParameters().containsKey("prompt"));
        assertNotEquals(silent.getState(), interactive.getState());
        assertNotEquals(
                silent.getAttributes().get("code_verifier"),
                interactive.getAttributes().get("code_verifier"));
        assertFalse(new HearthSilentAuthentication().pending(request));
        assertNull(resolver.resolve(request("/other")));
    }

    @Test
    void interactiveAuthorizationCannotBeOverwrittenByBackgroundStart() {
        var request = request("/oauth2/authorization/hearth");
        assertNotNull(resolver.resolve(request));
        var other = request("/oauth2/authorization/hearth");
        other.setSession(request.getSession());
        assertFalse(new HearthSilentAuthentication().begin(other));
        other.setParameter("silent", "true");
        other.getSession().setAttribute(HearthSilentAuthentication.ARMED, true);
        assertNull(resolver.resolve(other));
        other.getSession().setAttribute(HearthSilentAuthentication.INTERACTIVE, 1L);
        assertTrue(new HearthSilentAuthentication().begin(other));
    }
}
