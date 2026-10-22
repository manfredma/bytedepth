package manfred.bytedepth.adapter.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;

class RouteSessionIdResolverTest {
    private RouteSessionIdResolver resolver() {
        var cookie = new CookieHttpSessionIdResolver();
        var serializer = new DefaultCookieSerializer();
        serializer.setUseBase64Encoding(false);
        cookie.setCookieSerializer(serializer);
        return new RouteSessionIdResolver(cookie);
    }

    @Test
    void onlyExactVersionPrefixUsesHeaderForReadWriteAndExpiry() {
        var resolver = resolver();
        for (String path : List.of("/api/mobile/v1", "/api/mobile/v1/", "/api/mobile/v1/me")) {
            var request = new MockHttpServletRequest("GET", "/blog" + path);
            request.setContextPath("/blog");
            request.setCookies(new Cookie("SESSION", "web"));
            request.addHeader("X-App-Session", "mobile");
            assertThat(resolver.resolveSessionIds(request)).containsExactly("mobile");
            var response = new MockHttpServletResponse();
            resolver.setSessionId(request, response, "new");
            assertThat(response.getHeader("X-App-Session")).isEqualTo("new");
            resolver.expireSession(request, response);
            assertThat(response.getHeader("X-App-Session")).isEmpty();
            assertThat(response.getHeader("Set-Cookie")).isNull();
        }
        for (String path : List.of(
                "/",
                "/api/mobile/v10",
                "/api/mobile/v1other",
                "/api/mobile/v10/me",
                "/api%2fmobile/v1/me",
                "/api/mobile/v1%2fme",
                "/api/mobile/v1+extra/me")) {
            var request = new MockHttpServletRequest("GET", path);
            request.setCookies(new Cookie("SESSION", "web"));
            request.addHeader("X-App-Session", "mobile");
            assertThat(resolver.resolveSessionIds(request)).containsExactly("web");
            var response = new MockHttpServletResponse();
            resolver.setSessionId(request, response, "new");
            assertThat(response.getHeader("Set-Cookie")).contains("SESSION=new");
            resolver.expireSession(request, response);
            assertThat(response.getHeaders("Set-Cookie").toString()).contains("Max-Age=0");
            assertThat(response.getHeader("X-App-Session")).isNull();
        }
    }

    @Test
    void missingCredentialNeverFallsBackToOtherTransport() {
        var resolver = resolver();
        var mobile = new MockHttpServletRequest("GET", "/api/mobile/v1/me");
        mobile.setCookies(new Cookie("SESSION", "web"));
        assertThat(resolver.resolveSessionIds(mobile)).isEmpty();
        var web = new MockHttpServletRequest("GET", "/posts/new");
        web.addHeader("X-App-Session", "mobile");
        assertThat(resolver.resolveSessionIds(web)).isEmpty();
    }
}
