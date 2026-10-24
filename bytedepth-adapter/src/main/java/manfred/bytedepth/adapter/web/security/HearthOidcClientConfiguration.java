package manfred.bytedepth.adapter.web.security;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/** Creates the Hearth client only when explicitly enabled, keeping local/test contexts OAuth2-free. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "bytedepth.hearth-oidc", name = "enabled", havingValue = "true")
public class HearthOidcClientConfiguration {

    @Bean
    ClientRegistrationRepository hearthClientRegistration(
            @Value("${bytedepth.hearth-oidc.issuer}") String issuer,
            @Value("${bytedepth.hearth-oidc.client-id}") String clientId,
            @Value("${bytedepth.hearth-oidc.client-secret}") String clientSecret) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("hearth")
                .registrationId("hearth")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri(issuer + "/oauth2/authorize")
                .tokenUri(issuer + "/oauth2/token")
                .jwkSetUri(issuer + "/oauth2/jwks")
                .userInfoUri(issuer + "/userinfo")
                .userInfoAuthenticationMethod(AuthenticationMethod.HEADER)
                .userNameAttributeName("sub")
                .issuerUri(issuer)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", issuer + "/connect/logout"))
                .scope("openid", "profile", "email")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
