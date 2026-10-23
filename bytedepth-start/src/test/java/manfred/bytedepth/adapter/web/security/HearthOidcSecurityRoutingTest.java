package manfred.bytedepth.adapter.web.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.app.user.OidcIdentityAuthenticationPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(useDefaultFilters = false, excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@Import({SecurityConfig.class, HearthOidcSecurityConfiguration.class})
@TestPropertySource(properties = "bytedepth.hearth-oidc.enabled=true")
class HearthOidcSecurityRoutingTest {

    @Autowired
    private WebApplicationContext context;

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
    void enabledHearthConfigurationBuildsTheSecurityContext() {
        assertNotNull(context.getBean("hearthFilterChain"));
    }
}
