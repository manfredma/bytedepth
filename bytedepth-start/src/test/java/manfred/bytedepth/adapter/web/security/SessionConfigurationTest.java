package manfred.bytedepth.adapter.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionConfigurationTest {
    @Test
    void bootClasspathRegistersSessionAutoConfiguration() {
        var candidates = org.springframework.boot.context.annotation.ImportCandidates.load(
                org.springframework.boot.autoconfigure.AutoConfiguration.class,
                getClass().getClassLoader());
        assertThat(candidates)
                .contains(
                        "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration",
                        "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration");
    }

    @Test
    void bootWiresRedisFilterTimeoutNamespaceAndSecureCookieDelegateWithoutConnections() throws Exception {
        var factory = org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
                // The runner's preinstalled MockServletContext otherwise selects Boot's WAR cookie settings.
                // Our executable jar binds server.servlet.* through the embedded-server branch.
                .withInitializer(context -> context.setServletContext(null))
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.session.autoconfigure.SessionAutoConfiguration.class,
                        org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration
                                .class))
                .withBean(org.springframework.data.redis.connection.RedisConnectionFactory.class, () -> factory)
                .withUserConfiguration(SessionIdConfiguration.class, ResolverSpy.class)
                .withPropertyValues(
                        "spring.session.timeout=60m",
                        "spring.session.data.redis.namespace=bytedepth:test:mobile",
                        "server.servlet.session.cookie.secure=true",
                        "server.servlet.session.cookie.http-only=true",
                        "server.servlet.session.cookie.same-site=lax")
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(org.springframework.session.SessionRepository.class)
                            .hasBean("springSessionRepositoryFilter")
                            .hasBean("sessionRepositoryFilterRegistration")
                            .hasSingleBean(org.springframework.session.web.http.CookieSerializer.class);
                    var registration = context.getBean(
                            "sessionRepositoryFilterRegistration",
                            org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean.class);
                    assertThat(registration.getOrder())
                            .isLessThan(
                                    org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
                                            .DEFAULT_FILTER_ORDER);
                    var repository =
                            context.getBean(org.springframework.session.data.redis.RedisSessionRepository.class);
                    assertThat(((org.springframework.session.Session) repository.createSession())
                                    .getMaxInactiveInterval())
                            .isEqualTo(java.time.Duration.ofMinutes(60));
                    assertThat(org.springframework.test.util.ReflectionTestUtils.getField(repository, "keyNamespace"))
                            .isEqualTo("bytedepth:test:mobile:");
                    var resolver = context.getBean(org.springframework.session.web.http.HttpSessionIdResolver.class);
                    assertThat(resolver).isInstanceOf(RouteSessionIdResolver.class);
                    var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/");
                    request.setContextPath("");
                    var response = new org.springframework.mock.web.MockHttpServletResponse();
                    resolver.setSessionId(request, response, "web-session");
                    assertThat(response.getHeader("Set-Cookie")).contains("Secure", "HttpOnly", "SameSite=Lax");
                    var filter = context.getBean(org.springframework.session.web.http.SessionRepositoryFilter.class);
                    var mobile = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/mobile/v1/me");
                    filter.doFilter(
                            mobile,
                            new org.springframework.mock.web.MockHttpServletResponse(),
                            (req, res) -> assertThat(((jakarta.servlet.http.HttpServletRequest) req).getSession(false))
                                    .isNull());
                    var resolved = org.mockito.ArgumentCaptor.forClass(jakarta.servlet.http.HttpServletRequest.class);
                    org.mockito.Mockito.verify((RouteSessionIdResolver) resolver)
                            .resolveSessionIds(resolved.capture());
                    assertThat(((jakarta.servlet.http.HttpServletRequestWrapper) resolved.getValue()).getRequest())
                            .isSameAs(mobile);
                    org.mockito.Mockito.verifyNoInteractions(factory);
                });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"staging-it", "staging-e2e"})
    void actualStagingYamlOverridesBaseRedisSessionNamespace(String profile) {
        String expected = "bytedepth:test:isolated:" + profile;
        String variable = "BYTEDEPTH_"
                + profile.toUpperCase(java.util.Locale.ROOT).replace('-', '_') + "_REDIS_SESSION_NAMESPACE";
        var factory = org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
                .withInitializer(context -> {
                    context.setServletContext(null);
                    var sources = context.getEnvironment().getPropertySources();
                    sources.addFirst(yamlProperties("application.yml"));
                    sources.addFirst(yamlProperties("application-" + profile + ".yml"));
                })
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.session.autoconfigure.SessionAutoConfiguration.class,
                        org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration
                                .class))
                .withBean(org.springframework.data.redis.connection.RedisConnectionFactory.class, () -> factory)
                .withPropertyValues(variable + "=" + expected, "BYTEDEPTH_REDIS_SESSION_NAMESPACE=bytedepth:test:base")
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(org.springframework.session.data.redis.RedisSessionRepository.class);
                    var repository =
                            context.getBean(org.springframework.session.data.redis.RedisSessionRepository.class);
                    assertThat(org.springframework.test.util.ReflectionTestUtils.getField(repository, "keyNamespace"))
                            .as("%s must bind its Host-isolated Session namespace", profile)
                            .isEqualTo(expected + ":");
                    org.mockito.Mockito.verifyNoInteractions(factory);
                });
    }

    private static org.springframework.core.env.PropertiesPropertySource yamlProperties(String resource) {
        var yaml = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        yaml.setResources(new org.springframework.core.io.ClassPathResource(resource));
        return new org.springframework.core.env.PropertiesPropertySource(
                resource, java.util.Objects.requireNonNull(yaml.getObject()));
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class ResolverSpy {
        @org.springframework.context.annotation.Bean
        static org.springframework.beans.factory.config.BeanPostProcessor observeResolver() {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    return bean instanceof RouteSessionIdResolver ? org.mockito.Mockito.spy(bean) : bean;
                }
            };
        }
    }
}
