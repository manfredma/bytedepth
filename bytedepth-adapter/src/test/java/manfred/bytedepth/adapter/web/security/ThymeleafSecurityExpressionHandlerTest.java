package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThymeleafSecurityExpressionHandlerTest {

    private final ThymeleafSecurityExpressionHandler handler = new ThymeleafSecurityExpressionHandler();

    @Test
    void getExpressionParser_returnsDelegateParser() {
        assertThat(handler.getExpressionParser()).isNotNull();
    }

    @Test
    void createEvaluationContext_withAuthentication_returnsContext() {
        Authentication authentication = new TestingAuthenticationToken("admin", "pw", "ROLE_ADMIN");
        FilterInvocation invocation = filterInvocation();

        EvaluationContext context = handler.createEvaluationContext(authentication, invocation);

        assertThat(context).isNotNull();
        // 求值 authentication 表达式，触发 WebSecurityExpressionRoot 访问 supplier.get()
        //（覆盖 createEvaluationContext 内 () -> authentication 的 lambda）
        Object resolved = new SpelExpressionParser()
                .parseExpression("authentication").getValue(context);
        assertThat(resolved).isSameAs(authentication);
    }

    @Test
    void createEvaluationContext_withSupplier_returnsContext() {
        Supplier<Authentication> supplier = () -> new TestingAuthenticationToken("admin", "pw", "ROLE_ADMIN");
        FilterInvocation invocation = filterInvocation();

        EvaluationContext context = handler.createEvaluationContext(supplier, invocation);

        assertThat(context).isNotNull();
    }

    private static FilterInvocation filterInvocation() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/");
        when(request.getMethod()).thenReturn("GET");
        return new FilterInvocation(request, response, mock(FilterChain.class));
    }
}
