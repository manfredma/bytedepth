package manfred.bytedepth.adapter.web.util;

import manfred.bytedepth.adapter.web.security.SiteUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/** Security 上下文工具方法。 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** 从 SecurityContextHolder 读取当前用户，匿名返回 null。 */
    public static UserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof UserDetails ud ? ud : null;
    }

    /** 判断当前用户是否拥有指定权限。 */
    public static boolean hasAuthority(UserDetails user, String authority) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    /** 判断用户是否为内容的作者。 */
    public static boolean isOwner(UserDetails user, Long authorId) {
        if (user == null || authorId == null) return false;
        if (user instanceof SiteUserDetails sd) {
            return sd.getId().equals(authorId);
        }
        return false;
    }

    /** 从登录用户中提取数据库 ID，匿名返回 null。 */
    public static Long extractUserId(UserDetails user) {
        if (user instanceof SiteUserDetails sd) {
            return sd.getId();
        }
        return null;
    }
}