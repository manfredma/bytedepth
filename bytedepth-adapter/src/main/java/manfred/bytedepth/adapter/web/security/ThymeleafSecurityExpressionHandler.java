package manfred.bytedepth.adapter.web.security;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.DefaultHttpSecurityExpressionHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * 桥接 Thymeleaf extras 与 Spring Security 7 的 SecurityExpressionHandler。
 * <p>
 * Thymeleaf extras 3.1.5.RELEASE 要求 {@link SecurityExpressionHandler} 的泛型参数为
 * {@link FilterInvocation}（SS6 时代的类型）。Spring Security 7 将该类型改为
 * {@link RequestAuthorizationContext}，且 {@link DefaultHttpSecurityExpressionHandler}
 * 不再兼容 {@link FilterInvocation}。
 * <p>
 * 通过组合（而非继承）实现 {@code SecurityExpressionHandler<FilterInvocation>}，
 * 让 {@code GenericTypeResolver.resolveTypeArgument(...)} 能解析出 {@code FilterInvocation}，
 * 从而被 Thymeleaf extras 接受。实际求值委托给 {@link DefaultHttpSecurityExpressionHandler}。
 */
public class ThymeleafSecurityExpressionHandler implements SecurityExpressionHandler<FilterInvocation> {

    private final DefaultHttpSecurityExpressionHandler delegate = new DefaultHttpSecurityExpressionHandler();

    @Override
    public ExpressionParser getExpressionParser() {
        return delegate.getExpressionParser();
    }

    @Override
    public EvaluationContext createEvaluationContext(Authentication authentication, FilterInvocation invocation) {
        return delegate.createEvaluationContext(() -> authentication, toRequestContext(invocation));
    }

    @Override
    public EvaluationContext createEvaluationContext(Supplier<? extends Authentication> authentication, FilterInvocation invocation) {
        return delegate.createEvaluationContext(authentication, toRequestContext(invocation));
    }

    private static RequestAuthorizationContext toRequestContext(FilterInvocation invocation) {
        return new RequestAuthorizationContext(invocation.getRequest());
    }
}
