package manfred.bytedepth.adapter.web.portal;

import jakarta.servlet.http.Cookie;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class AnnotationVisitorIdentityCoverageTest {

    @Test
    void existingHash_returnsNullWithoutAVisitorCookie() {
        assertThat(new AnnotationVisitorIdentity().existingHash(new MockHttpServletRequest())).isNull();
    }

    @Test
    void existingHash_treatsBlankCookiesAsNoIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnnotationVisitorIdentity.COOKIE_NAME, " "));

        assertThat(new AnnotationVisitorIdentity().existingHash(request)).isNull();
    }

    @Test
    void existingHash_failsClearlyWhenSha256IsUnavailable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnnotationVisitorIdentity.COOKIE_NAME, "visitor"));

        try (var digest = mockStatic(MessageDigest.class)) {
            digest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("unavailable"));

            assertThatThrownBy(() -> new AnnotationVisitorIdentity().existingHash(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256");
        }
    }
}
