package manfred.bytedepth.adapter.web.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/** Web 请求工具方法。 */
public final class WebUtils {

    private WebUtils() {}

    /**
     * 获取客户端真实 IP：优先取 X-Forwarded-For 首个非私有 IP，
     * 无法解析时回退到 RemoteAddr。
     */
    public static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            for (String part : xff.split(",")) {
                String ip = part.trim();
                if (!ip.isBlank() && !isPrivateIp(ip)) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }

    /** 判断是否为私有/回环 IP（简单字符串匹配）。 */
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    static boolean isPrivateIp(String ip) {
        return ip.startsWith("10.") || isPrivate172Range(ip) || ip.startsWith("192.168.")
                || ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1");
    }

    /** 判断是否为 172.16.0.0/12 私有范围。 */
    private static boolean isPrivate172Range(String ip) {
        if (!ip.startsWith("172.") || ip.length() < 6) return false;
        int dot = ip.indexOf('.', 4);
        if (dot < 0) return false;
        try {
            int second = Integer.parseInt(ip.substring(4, dot));
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    /** 截断字符串至指定长度，null 安全。 */
    public static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    /** 从请求中读取指定名称的 Cookie 值，未找到返回 null。 */
    public static String readCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
