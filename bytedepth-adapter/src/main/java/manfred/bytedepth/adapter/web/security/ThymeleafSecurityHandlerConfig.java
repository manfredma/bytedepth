package manfred.bytedepth.adapter.web.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.DefaultHttpSecurityExpressionHandler;

/**
 * 为 Thymeleaf 模板中的 sec:authorize 提供 Spring Security 表达式处理支持。
 * <p>
 * 在 {@code @WebMvcTest} 等切片测试中需通过 {@code @Import} 引入此类。
 */
@Configuration
public class ThymeleafSecurityHandlerConfig {

    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SecurityExpressionHandler<FilterInvocation> securityExpressionHandler() {
        return (SecurityExpressionHandler) new DefaultHttpSecurityExpressionHandler();
    }
}