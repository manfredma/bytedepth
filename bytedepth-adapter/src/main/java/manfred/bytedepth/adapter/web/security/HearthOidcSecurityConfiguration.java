package manfred.bytedepth.adapter.web.security;

import manfred.bytedepth.app.user.OidcIdentityAuthenticationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/** Isolates Hearth endpoints from the application's existing local form-login filter chain. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "bytedepth.hearth-oidc", name = "enabled", havingValue = "true")
public class HearthOidcSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain hearthFilterChain(
            HttpSecurity http, ClientRegistrationRepository registrations, OidcIdentityAuthenticationPort identities)
            throws Exception {
        HearthOidcAuthenticationSuccessHandler successHandler =
                new HearthOidcAuthenticationSuccessHandler(identities, new HttpSessionSecurityContextRepository());
        OidcClientInitiatedLogoutSuccessHandler logout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logout.setPostLogoutRedirectUri("{baseUrl}/login");
        http.securityMatcher("/oauth2/**", "/login/oauth2/**", "/logout")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth.loginPage("/oauth2/authorization/hearth")
                        .authorizationEndpoint(endpoint ->
                                endpoint.authorizationRequestResolver(pkceAuthorizationRequestResolver(registrations)))
                        .successHandler(successHandler))
                .logout(logoutSpec -> logoutSpec
                        .deleteCookies("bytedepth-remember-me")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            // The application uses SiteUserDetails after login. Recover only
                            // the verified OIDC identity needed by the protocol logout handler.
                            var protocolAuthentication = authentication != null
                                            && authentication.getDetails() instanceof OAuth2AuthenticationToken oidc
                                    ? oidc
                                    : authentication;
                            logout.onLogoutSuccess(request, response, protocolAuthentication);
                        })
                        .permitAll());
        return http.build();
    }

    static OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
            ClientRegistrationRepository registrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }
}
