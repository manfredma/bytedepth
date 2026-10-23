package manfred.bytedepth.adapter.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class HearthOidcClientConfigurationTest {

    @Test
    void createsStandardHearthAuthorizationCodeClient() {
        ClientRegistrationRepository repository = new HearthOidcClientConfiguration()
                .hearthClientRegistration("https://hearth.example", "bytedepth", "secret");

        var registration = repository.findByRegistrationId("hearth");
        assertEquals(
                "https://hearth.example/oauth2/authorize",
                registration.getProviderDetails().getAuthorizationUri());
        assertEquals(
                "https://hearth.example/oauth2/token",
                registration.getProviderDetails().getTokenUri());
        assertEquals("https://hearth.example", registration.getProviderDetails().getIssuerUri());
        assertEquals(
                "https://hearth.example/connect/logout",
                registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint"));
    }
}
