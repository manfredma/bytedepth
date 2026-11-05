package manfred.bytedepth.adapter.web.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class HearthAuthorizationRequestRepositoryTest {
    private final HearthAuthorizationRequestRepository repository = new HearthAuthorizationRequestRepository();

    @Test
    void callbackTypeComesOnlyFromMatchedFrameworkAuthorization() {
        for (boolean silent : new boolean[] {true, false}) {
            var start = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            var builder = OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri("https://hearth.example/oauth2/authorize")
                    .clientId("test")
                    .redirectUri("https://business.example/login/oauth2/code/hearth")
                    .state("test-state");
            if (silent) builder.additionalParameters(parameters -> parameters.put("prompt", "none"));
            var authorization = builder.build();
            repository.saveAuthorizationRequest(authorization, start, response);
            var callback = new MockHttpServletRequest();
            callback.setSession(start.getSession());
            callback.setParameter("state", "wrong-state");
            assertNull(repository.removeAuthorizationRequest(callback, response));
            assertFalse(new HearthSilentAuthentication().pending(callback));
            callback.setParameter("state", "test-state");
            assertSame(authorization, repository.loadAuthorizationRequest(callback));
            assertSame(authorization, repository.removeAuthorizationRequest(callback, response));
            assertEquals(silent, new HearthSilentAuthentication().pending(callback));
            assertNull(repository.loadAuthorizationRequest(callback));
            repository.saveAuthorizationRequest(null, callback, response);
        }
    }
}
