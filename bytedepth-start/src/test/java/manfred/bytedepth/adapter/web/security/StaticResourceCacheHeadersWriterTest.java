package manfred.bytedepth.adapter.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

class StaticResourceCacheHeadersWriterTest {
    private MockHttpServletResponse headers(String path, Object handler, int status) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handler);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        new CacheControlHeadersWriter().writeHeaders(request, response);
        HeaderWriter writer = new StaticResourceCacheHeadersWriter();
        writer.writeHeaders(request, response);
        return response;
    }

    @Test
    void fingerprintedPublicResourcesAreImmutableIncludingConditionalAndRangeResponses() {
        for (String path : List.of(
                "/vendor/mermaid/mermaid.min-0123456789abcdef0123456789abcdef.js",
                "/css/theme-0123456789abcdef0123456789abcdef.css",
                "/js/nav-0123456789abcdef0123456789abcdef.js",
                "/fonts/nested/font-0123456789abcdef0123456789abcdef.woff2",
                "/icons/logo-0123456789abcdef0123456789abcdef.svg")) {
            for (int status : List.of(200, 206, 304)) {
                MockHttpServletResponse response = headers(path, new ResourceHttpRequestHandler(), status);
                assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                        .as("%s status %s", path, status)
                        .isEqualTo("max-age=31536000, public, immutable");
                assertThat(response.getHeader(HttpHeaders.PRAGMA)).isNull();
                assertThat(response.getHeader(HttpHeaders.EXPIRES)).isNull();
            }
        }
    }

    @Test
    void unversionedPublicResourcesMustRevalidate() {
        for (String path : List.of("/images/upload.svg", "/icons/logo.svg", "/css/theme.css", "/manifest.json")) {
            assertThat(headers(path, new ResourceHttpRequestHandler(), 200).getHeader(HttpHeaders.CACHE_CONTROL))
                    .isEqualTo("no-cache");
        }
    }

    @Test
    void dynamicResponsesAndResourceErrorsRetainNoStore() {
        for (String path : List.of("/", "/posts/private", "/sw.js", "/js/fake-0123456789abcdef0123456789abcdef.js")) {
            assertThat(headers(path, new Object(), 200).getHeader(HttpHeaders.CACHE_CONTROL))
                    .contains("no-store");
        }
        for (int status : List.of(302, 400, 403, 404, 500)) {
            assertThat(headers(
                                    "/css/fake-0123456789abcdef0123456789abcdef.css",
                                    new ResourceHttpRequestHandler(),
                                    status)
                            .getHeader(HttpHeaders.CACHE_CONTROL))
                    .contains("no-store");
        }
    }
}
