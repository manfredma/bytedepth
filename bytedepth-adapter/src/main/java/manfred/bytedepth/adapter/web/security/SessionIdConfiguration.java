package manfred.bytedepth.adapter.web.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer;

@Configuration(proxyBeanMethods = false)
public class SessionIdConfiguration {
    // Exposing the concrete cookie delegate preserves Boot's CookieSerializer auto-configuration.
    // Reconstructing the serializer here would silently discard Secure/SameSite deployment settings.
    @Bean
    public CookieHttpSessionIdResolver webCookieSessionIdResolver(CookieSerializer serializer) {
        var resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(serializer);
        return resolver;
    }

    @Bean
    @Primary
    public RouteSessionIdResolver httpSessionIdResolver(
            @Qualifier("webCookieSessionIdResolver") CookieHttpSessionIdResolver web) {
        return new RouteSessionIdResolver(web);
    }
}
