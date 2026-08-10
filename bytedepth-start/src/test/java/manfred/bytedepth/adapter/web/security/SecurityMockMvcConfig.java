package manfred.bytedepth.adapter.web.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * 让 {@code @WebMvcTest} 自动注入的 MockMvc 应用 Spring Security 配置器。
 * <p>
 * Spring Boot 4.x 中 {@code @WebMvcTest} 不再自动应用
 * {@code SecurityMockMvcConfigurers.springSecurity()}。缺少该配置器时，
 * {@code @WithMockUser} 设置的 {@code SecurityContextHolder} 认证不会传到请求，
 * 导致安全断言（403/200）退化为 302 跳转登录页。此 bean 恢复该行为。
 */
@TestConfiguration
public class SecurityMockMvcConfig {

    @Bean
    public MockMvcBuilderCustomizer securityMockMvcBuilderCustomizer() {
        return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
    }
}
