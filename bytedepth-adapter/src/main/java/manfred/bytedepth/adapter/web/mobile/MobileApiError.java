package manfred.bytedepth.adapter.web.mobile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import tools.jackson.databind.json.JsonMapper;

/** The same wire envelope is used by MVC, authentication and rate-limit boundaries. */
public record MobileApiError(String code, String message) {
    public static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonMapper.builder().build().writeValueAsString(new MobileApiError(code, message)));
    }
}
