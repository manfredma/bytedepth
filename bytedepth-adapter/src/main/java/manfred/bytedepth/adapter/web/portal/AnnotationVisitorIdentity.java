package manfred.bytedepth.adapter.web.portal;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import manfred.bytedepth.adapter.web.util.WebUtils;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/** 为未登录读者维护可撤销、不可从数据库反推的批注归属身份。 */
@Component
public class AnnotationVisitorIdentity {

    static final String COOKIE_NAME = "bd_annotation_visitor";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String existingHash(HttpServletRequest request) {
        String token = WebUtils.readCookie(request, COOKIE_NAME);
        return token == null || token.isBlank() ? null : sha256(token);
    }

    public String getOrCreateHash(HttpServletRequest request, HttpServletResponse response) {
        String existing = existingHash(request);
        if (existing != null) {
            return existing;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(400))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return sha256(token);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }
}
