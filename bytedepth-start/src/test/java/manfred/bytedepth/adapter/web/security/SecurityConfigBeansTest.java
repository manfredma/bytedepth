package manfred.bytedepth.adapter.web.security;

import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigBeansTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void infrastructureBeans_areConfiguredWithTheirDependencies() {
        PasswordEncoder encoder = config.passwordEncoder();
        DaoAuthenticationProvider provider = config.authenticationProvider(mock(org.springframework.security.core.userdetails.UserDetailsService.class), encoder);

        assertNotNull(provider);
        assertTrue(encoder.matches("secret", encoder.encode("secret")));
    }

    @Test
    void rateLimitFilterRegistration_isDisabledToAvoidDoubleCharging() {
        var filter = config.rateLimitFilter(mock(RateLimitPort.class), new RateLimitProperties(), mock(ResourceLoader.class));
        var registration = config.rateLimitFilterRegistration(filter);

        assertNotNull(filter);
        assertTrue(!registration.isEnabled());
    }

    @Test
    void rememberMeServices_isTokenBasedToAvoidConcurrentTokenRotationDrop() {
        var uds = mock(org.springframework.security.core.userdetails.UserDetailsService.class);
        var services = config.rememberMeServices("key", uds, false);
        org.junit.jupiter.api.Assertions.assertInstanceOf(
            org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.class,
            services);
    }
}
