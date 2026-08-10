package manfred.bytedepth.adapter.web.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebUtilsTest {

    // ── getClientIp：X-Forwarded-For 优先 ───────────────────

    @Test
    void getClientIp_xffHasPublicIp_returnsFirstPublicIp() {
        HttpServletRequest request = requestWithXff("10.0.0.1, 203.0.113.5");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(WebUtils.getClientIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void getClientIp_xffAllPrivate_fallsBackToRemoteAddr() {
        HttpServletRequest request = requestWithXff("10.0.0.1, 172.16.0.1, 192.168.1.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(WebUtils.getClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void getClientIp_noXffHeader_returnsRemoteAddr() {
        HttpServletRequest request = requestWithXff(null);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");

        assertThat(WebUtils.getClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void getClientIp_blankXff_returnsRemoteAddr() {
        HttpServletRequest request = requestWithXff("  ");
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");

        assertThat(WebUtils.getClientIp(request)).isEqualTo("203.0.113.9");
    }

    // ── isPrivateIp ────────────────────────────────────────

    @Test
    void isPrivateIp_privateAddresses_returnsTrue() {
        assertThat(WebUtils.isPrivateIp("10.1.2.3")).isTrue();
        assertThat(WebUtils.isPrivateIp("172.16.0.1")).isTrue();
        assertThat(WebUtils.isPrivateIp("192.168.1.1")).isTrue();
        assertThat(WebUtils.isPrivateIp("127.0.0.1")).isTrue();
        assertThat(WebUtils.isPrivateIp("::1")).isTrue();
        assertThat(WebUtils.isPrivateIp("0:0:0:0:0:0:0:1")).isTrue();
    }

    @Test
    void isPrivateIp_publicAddress_returnsFalse() {
        assertThat(WebUtils.isPrivateIp("203.0.113.5")).isFalse();
        assertThat(WebUtils.isPrivateIp("8.8.8.8")).isFalse();
    }

    // ── truncate ───────────────────────────────────────────

    @Test
    void truncate_null_returnsNull() {
        assertThat(WebUtils.truncate(null, 10)).isNull();
    }

    @Test
    void truncate_shorterOrEqual_returnsSame() {
        assertThat(WebUtils.truncate("abc", 5)).isEqualTo("abc");
        assertThat(WebUtils.truncate("abcde", 5)).isEqualTo("abcde");
    }

    @Test
    void truncate_longer_returnsTruncated() {
        assertThat(WebUtils.truncate("abcdef", 3)).isEqualTo("abc");
    }

    // ── readCookie ─────────────────────────────────────────

    @Test
    void readCookie_noCookies_returnsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);

        assertThat(WebUtils.readCookie(request, "name")).isNull();
    }

    @Test
    void readCookie_found_returnsValue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("other", "x"),
                new Cookie("name", "value")});

        assertThat(WebUtils.readCookie(request, "name")).isEqualTo("value");
    }

    @Test
    void readCookie_notFound_returnsNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "x")});

        assertThat(WebUtils.readCookie(request, "name")).isNull();
    }

    private static HttpServletRequest requestWithXff(String xff) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xff);
        return request;
    }
}
