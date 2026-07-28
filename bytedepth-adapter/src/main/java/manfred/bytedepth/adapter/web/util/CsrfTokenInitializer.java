package manfred.bytedepth.adapter.web.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

public final class CsrfTokenInitializer {

    private CsrfTokenInitializer() {
    }

    public static void initialize(HttpServletRequest request) {
        Object value = request.getAttribute(CsrfToken.class.getName());
        if (value instanceof CsrfToken token) {
            token.getToken();
        }
    }
}
