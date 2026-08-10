package manfred.bytedepth.adapter.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.web.FilterInvocation;

/**
 * 为 Thymeleaf 模板中的 sec:authorize 提供 Spring Security 表达式处理支持。
 * <p>
 * Spring Boot 4.x / Spring Security 7 移除了 WebSecurityExpressionHandler，
 * 而 thymeleaf-extras-springsecurity6 3.1.5.RELEASE 仍要求
 * {@code SecurityExpressionHandler<FilterInvocation>}。此处注册兼容适配器。
 * <p>
 * 生产环境由组件扫描自动加载；{@code @WebMvcTest} 等切片测试需通过 {@code @Import} 引入。
 */
@Configuration
public class ThymeleafSecurityHandlerConfig {

    @Bean
    public SecurityExpressionHandler<FilterInvocation> securityExpressionHandler() {
        return new ThymeleafSecurityExpressionHandler();
    }
}
