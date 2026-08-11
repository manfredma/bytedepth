package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.HttpServletResponse;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitFilter;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 开启 @PreAuthorize 方法级权限
public class SecurityConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitPort rateLimitPort, RateLimitProperties properties,
                                        ResourceLoader resourceLoader) {
        return new RateLimitFilter(rateLimitPort, properties, resourceLoader);
    }

    /** The filter is invoked by Spring Security only; registering it with the servlet too would charge twice. */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           PersistentTokenRepository persistentTokenRepository,
                                           UserDetailsService userDetailsService,
                                           RateLimitFilter rateLimitFilter,
                                           @Value("${BYTEDEPTH_REMEMBER_ME_KEY:bytedepth-local-remember-me-key}") String rememberMeKey,
                                           @Value("${BYTEDEPTH_REMEMBER_ME_COOKIE_SECURE:false}") boolean rememberMeCookieSecure) throws Exception {
        PersistentTokenBasedRememberMeServices rememberMeServices = new PersistentTokenBasedRememberMeServices(
            rememberMeKey, userDetailsService, persistentTokenRepository);
        rememberMeServices.setParameter("remember-me");
        rememberMeServices.setCookieName("bytedepth-remember-me");
        rememberMeServices.setTokenValiditySeconds(30 * 24 * 60 * 60);
        rememberMeServices.setUseSecureCookie(rememberMeCookieSecure);
        rememberMeServices.setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
        rememberMeServices.afterPropertiesSet();

        // CSRF：默认 HttpSessionCsrfTokenRepository + Thymeleaf 自动注入 _csrf hidden input。
        // 之前用 CookieCsrfTokenRepository，登录成功后 CsrfAuthenticationStrategy 清除
        // XSRF-TOKEN cookie 却不重新下发，导致后续 POST 表单（退出等）403。session 仓库
        // 把 token 存 session、提交时从 session 校验，不依赖 cookie 下发，更可靠。
        http
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/admin/search/**")
                .ignoringRequestMatchers(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/posts/*/reading-progress"))
            )
            .authorizeHttpRequests(auth -> auth
                // 个人内容工作区：Controller 继续按作者归属做数据与操作校验。
                .requestMatchers(HttpMethod.GET, "/admin")
                    .hasAnyAuthority("admin:dashboard:view", "blog:post:create", "blog:series:create:own")
                .requestMatchers(HttpMethod.POST, "/admin/posts/*/series")
                    .hasAuthority("admin:dashboard:view")
                .requestMatchers("/admin/posts/**").hasAnyAuthority("admin:dashboard:view", "blog:post:create")
                .requestMatchers("/admin/series/**")
                    .hasAnyAuthority("admin:dashboard:view", "blog:series:create:own", "blog:series:edit:own")
                .requestMatchers(HttpMethod.POST, "/admin/images/upload")
                    .hasAnyAuthority("admin:dashboard:view", "blog:post:create")
                // 后台：需要 admin 仪表盘权限（粗粒度守卫，各方法再用 @PreAuthorize 细化）
                .requestMatchers("/admin/**").hasAuthority("admin:dashboard:view")
                // 写评论、创建/发布文章、批注：至少需要登录
                .requestMatchers(HttpMethod.POST, "/posts/*/comments").authenticated()
                .requestMatchers(HttpMethod.POST, "/posts/*/annotations").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/posts/*/annotations/*").authenticated()
                .requestMatchers("/posts/new").authenticated()
                .requestMatchers(HttpMethod.POST, "/posts").authenticated()
                .requestMatchers(HttpMethod.POST, "/posts/*/publish").authenticated()
                // 其余全部放行（含 /register、/login、公开文章列表等）
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)   // 登录成功回首页，管理员自行导航到后台
                .permitAll()
            )
            .rememberMe(rememberMe -> rememberMe
                .rememberMeServices(rememberMeServices)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                    res.sendRedirect("/login"))
                .accessDeniedHandler((req, res, ex) ->
                    res.sendError(HttpServletResponse.SC_FORBIDDEN))
            );
        return http.build();
    }
}
