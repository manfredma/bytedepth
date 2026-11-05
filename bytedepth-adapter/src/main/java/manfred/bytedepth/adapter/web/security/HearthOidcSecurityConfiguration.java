package manfred.bytedepth.adapter.web.security;

import manfred.bytedepth.app.user.OidcIdentityAuthenticationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/** Isolates Hearth endpoints from the application's existing local form-login filter chain. */
@Configuration(proxyBeanMethods = false)
@Import(HearthSilentAuthenticationController.class)
@ConditionalOnProperty(prefix = "bytedepth.hearth-oidc", name = "enabled", havingValue = "true")
public class HearthOidcSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain hearthFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository registrations,
            OidcIdentityAuthenticationPort identities,
            @Value("${bytedepth.web-session-timeout-seconds:600}") int webTimeout)
            throws Exception {
        var silent = new HearthSilentAuthentication();
        HearthOidcAuthenticationSuccessHandler successHandler =
                new HearthOidcAuthenticationSuccessHandler(identities, new HttpSessionSecurityContextRepository());
        OidcClientInitiatedLogoutSuccessHandler logout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logout.setPostLogoutRedirectUri("{baseUrl}/login");
        http.securityMatcher("/oauth2/**", "/login/oauth2/**", "/logout", "/auth/**")
                .addFilterAfter(new WebSessionTimeoutFilter(webTimeout), SecurityContextHolderFilter.class)
                .addFilterAfter(new HearthLogoutSuppressionFilter(), WebSessionTimeoutFilter.class)
                .headers(headers -> headers.frameOptions(frame -> frame.disable())
                        .addHeaderWriter((req, res) -> {
                            res.setHeader(
                                    "X-Frame-Options",
                                    (req.getContextPath() + "/auth/silent-complete").equals(req.getRequestURI())
                                            ? "SAMEORIGIN"
                                            : "DENY");
                            res.setHeader("Cache-Control", "no-store");
                        }))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth -> oauth.loginPage("/oauth2/authorization/hearth")
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
                                        pkceAuthorizationRequestResolver(registrations))
                                .authorizationRequestRepository(new HearthAuthorizationRequestRepository()))
                        .successHandler(successHandler)
                        .failureHandler(silent::failure))
                .logout(logoutSpec -> logoutSpec
                        .deleteCookies("bytedepth-remember-me")
                        .addLogoutHandler((req, res, auth) -> silent.logout(req, res))
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
        return new HearthAuthorizationRequestResolver(registrations);
    }
}
