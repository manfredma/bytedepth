package manfred.bytedepth.app.user;

import java.util.List;

/** Authentication data owned by the application boundary, independent of Spring Security. */
public record UserAuthentication(
        Long id,
        String username,
        String passwordHash,
        String status,
        List<String> permissionCodes
) {
}
