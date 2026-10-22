package manfred.bytedepth.adapter.web.security;

import manfred.bytedepth.adapter.web.mobile.MobileApiError;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;

@Configuration(proxyBeanMethods = false)
public class MobileSecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain mobileFilterChain(
            HttpSecurity http, UserDetailsService users, PasswordEncoder encoder, RateLimitFilter rateLimit)
            throws Exception {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        http.securityMatcher(RouteSessionIdResolver::isMobileRequest)
                .authenticationProvider(provider)
                .addFilterBefore(rateLimit, UsernamePasswordAuthenticationFilter.class)
                .requestCache(AbstractHttpConfigurer::disable)
                .rememberMe(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.addHeaderWriter((req, res) -> res.setHeader("Cache-Control", "no-store")))
                // Keep the standard Session CSRF repository and authentication/session-fixation strategies.
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                HttpMethod.GET,
                                "/api/mobile/v1/csrf",
                                "/api/mobile/v1/posts/**",
                                "/api/mobile/v1/discovery",
                                "/api/mobile/v1/search",
                                "/api/mobile/v1/columns/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/mobile/v1/login", "/api/mobile/v1/registrations")
                        .permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/mobile/v1/posts/*/rating")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/mobile/v1/posts/*/comments")
                        .hasAuthority("blog:comment:create")
                        .requestMatchers(
                                "/api/mobile/v1/me",
                                "/api/mobile/v1/me/annotations",
                                "/api/mobile/v1/posts/*/annotations",
                                "/api/mobile/v1/posts/*/annotations/*")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .formLogin(form -> form.loginProcessingUrl("/api/mobile/v1/login")
                        .successHandler((req, res, auth) -> res.setStatus(204))
                        .failureHandler(
                                (req, res, ex) -> MobileApiError.write(res, 401, "AUTHENTICATION_FAILED", "登录失败")))
                .logout(logout -> logout.logoutUrl("/api/mobile/v1/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                                (req, res, ex) -> MobileApiError.write(res, 401, "UNAUTHENTICATED", "请先登录"))
                        .accessDeniedHandler((req, res, ex) -> MobileApiError.write(
                                res, 403, ex instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN", "请求被拒绝")));
        return http.build();
    }
}
