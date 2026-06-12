package manfred.bytedepth.domain.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URL slug 工具：从任意文本提取小写英文 + 数字组合，用 {@code -} 连接。
 * <p>格式约束：仅含 {@code [a-z0-9-]}，不以 {@code -} 开头/结尾，连续 {@code -} 合并为一个。</p>
 */
public final class SlugUtils {

    private static final int MAX_LENGTH = 80;
    private static final Pattern ALNUM = Pattern.compile("[a-zA-Z0-9]+");
    /** 合法 slug：小写英数字与 -，至少 2 字符，首尾为英数字 */
    private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9\\-]*[a-z0-9]$|^[a-z0-9]$");

    private SlugUtils() {}

    /**
     * 从任意文本生成 slug。
     * <ul>
     *   <li>提取所有 ASCII 字母 + 数字片段，小写后以 {@code -} 连接</li>
     *   <li>超过 {@value MAX_LENGTH} 字符时截断，去除末尾 {@code -}</li>
     *   <li>结果长度 &lt; 3 时返回空字符串（调用方负责补 fallback）</li>
     * </ul>
     */
    public static String slugify(String text) {
        if (text == null || text.isBlank()) return "";
        Matcher m = ALNUM.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (!sb.isEmpty()) sb.append('-');
            sb.append(m.group().toLowerCase());
            if (sb.length() >= MAX_LENGTH) break;
        }
        String result = sb.length() > MAX_LENGTH
                ? sb.substring(0, MAX_LENGTH).replaceAll("-+$", "")
                : sb.toString();
        return result.length() >= 3 ? result : "";
    }

    /**
     * 校验 slug 格式：仅含 {@code [a-z0-9-]}，首尾为英数字，无连续 {@code -}。
     */
    public static boolean isValid(String slug) {
        if (slug == null || slug.isBlank()) return false;
        return VALID.matcher(slug).matches() && !slug.contains("--");
    }
}
