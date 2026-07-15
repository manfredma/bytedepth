package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 开启 @PreAuthorize 方法级权限
public class SecurityConfig {

    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        repository.setCreateTableOnStartup(false);
        return repository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           PersistentTokenRepository persistentTokenRepository,
                                           @Value("${BYTEDEPTH_REMEMBER_ME_KEY:bytedepth-local-remember-me-key}") String rememberMeKey,
                                           @Value("${BYTEDEPTH_REMEMBER_ME_COOKIE_SECURE:false}") boolean rememberMeCookieSecure) throws Exception {
        // CSRF：默认 HttpSessionCsrfTokenRepository + Thymeleaf 自动注入 _csrf hidden input。
        // 之前用 CookieCsrfTokenRepository，登录成功后 CsrfAuthenticationStrategy 清除
        // XSRF-TOKEN cookie 却不重新下发，导致后续 POST 表单（退出等）403。session 仓库
        // 把 token 存 session、提交时从 session 校验，不依赖 cookie 下发，更可靠。
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/admin/search/**")
            )
            .authorizeHttpRequests(auth -> auth
                // 后台：需要 admin 仪表盘权限（粗粒度守卫，各方法再用 @PreAuthorize 细化）
                .requestMatchers("/admin/**").hasAuthority("admin:dashboard:view")
                // 写评论、创建/发布文章：至少需要登录
                .requestMatchers(HttpMethod.POST, "/posts/*/comments").authenticated()
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
                .tokenRepository(persistentTokenRepository)
                .rememberMeParameter("remember-me")
                .rememberMeCookieName("bytedepth-remember-me")
                .tokenValiditySeconds(30 * 24 * 60 * 60)
                .useSecureCookie(rememberMeCookieSecure)
                .key(rememberMeKey)
            )
            .logout(logout -> logout
                .addLogoutHandler((request, response, authentication) -> {
                    if (authentication != null) {
                        persistentTokenRepository.removeUserTokens(authentication.getName());
                    }
                })
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
