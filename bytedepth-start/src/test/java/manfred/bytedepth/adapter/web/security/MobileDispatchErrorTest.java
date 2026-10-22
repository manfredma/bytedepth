package manfred.bytedepth.adapter.web.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class MobileDispatchErrorTest extends MobileSessionLifecycleTest {
    @Test
    void unsupportedRegistrationContentTypeReturnsClientJsonError() throws Exception {
        var token = csrf(null);
        mvc.perform(post("/api/mobile/v1/registrations")
                        .header("X-App-Session", token.id())
                        .header("X-CSRF-TOKEN", token.token())
                        .contentType("text/plain")
                        .content("unsupported"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void missingMobileResourceUsesJsonAndWebMissingResourceKeepsHtmlView() throws Exception {
        mvc.perform(get("/api/mobile/v1/posts/missing/resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/web-missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void unsupportedMobileMethodUsesJson() throws Exception {
        var id = login(csrf(null)).getResponse().getHeader("X-App-Session");
        var token = csrf(id);
        mvc.perform(post("/api/mobile/v1/me").header("X-App-Session", id).header("X-CSRF-TOKEN", token.token()))
                .andExpect(header().string("Allow", "GET"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unacceptableRepresentationUsesJsonClientError() throws Exception {
        mvc.perform(get("/api/mobile/v1/csrf").accept("application/xml"))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }
}
