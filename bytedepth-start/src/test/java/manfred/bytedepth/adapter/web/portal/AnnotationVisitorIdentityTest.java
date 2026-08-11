package manfred.bytedepth.adapter.web.portal;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationVisitorIdentityTest {
    private final AnnotationVisitorIdentity identity = new AnnotationVisitorIdentity();

    @Test
    void getOrCreateHash_setsSecureHttpOnlyCookieAndCanBeReadBack() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String hash = identity.getOrCreateHash(request, response);
        String header = response.getHeader("Set-Cookie");
        assertThat(hash).hasSize(64);
        assertThat(header).contains("bd_annotation_visitor=", "HttpOnly", "Secure", "SameSite=Lax");
        String token = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        MockHttpServletRequest repeated = new MockHttpServletRequest();
        repeated.setCookies(new Cookie(AnnotationVisitorIdentity.COOKIE_NAME, token));
        assertThat(identity.existingHash(repeated)).isEqualTo(hash);
    }

    @Test
    void existingToken_doesNotSetAnotherCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnnotationVisitorIdentity.COOKIE_NAME, "visitor-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(identity.getOrCreateHash(request, response)).isEqualTo(identity.existingHash(request));
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }
}
