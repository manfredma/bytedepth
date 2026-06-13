package manfred.bytedepth.adapter.web.util;

import java.util.regex.Pattern;

/**
 * SEO 辅助工具：从 Markdown 正文提取纯文字摘要，用于 {@code <meta name="description">}。
 */
public final class SeoUtils {

    private static final int DEFAULT_MAX_LEN = 160;

    // 代码块（围栏式）
    private static final Pattern CODE_BLOCK  = Pattern.compile("```[\\s\\S]*?```", Pattern.DOTALL);
    // ATX 标题
    private static final Pattern HEADING     = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE);
    // 图片
    private static final Pattern IMAGE       = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    // 链接 → 保留文字
    private static final Pattern LINK        = Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    // 行内代码
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]+`");
    // 粗体 / 斜体 → 保留文字
    private static final Pattern EMPHASIS    = Pattern.compile("[*_]{1,3}([^*_\n]+)[*_]{1,3}");
    // 引用块
    private static final Pattern BLOCKQUOTE  = Pattern.compile("^>+\\s*", Pattern.MULTILINE);
    // 分割线
    private static final Pattern HR          = Pattern.compile("^[-*_]{3,}\\s*$", Pattern.MULTILINE);
    // 连续空白 → 单空格
    private static final Pattern WHITESPACE  = Pattern.compile("\\s+");

    private SeoUtils() {}

    /** 默认摘要长度：160 字符。 */
    public static String excerpt(String markdown) {
        return excerpt(markdown, DEFAULT_MAX_LEN);
    }

    /**
     * 去除 Markdown 语法后截取前 {@code maxLen} 个字符。
     * 在单词边界截断，末尾加省略号。
     */
    public static String excerpt(String markdown, int maxLen) {
        if (markdown == null || markdown.isBlank()) return "";
        String t = markdown;
        t = CODE_BLOCK.matcher(t).replaceAll(" ");
        t = HEADING.matcher(t).replaceAll("");
        t = IMAGE.matcher(t).replaceAll("");
        t = LINK.matcher(t).replaceAll("$1");
        t = INLINE_CODE.matcher(t).replaceAll("");
        t = EMPHASIS.matcher(t).replaceAll("$1");
        t = BLOCKQUOTE.matcher(t).replaceAll("");
        t = HR.matcher(t).replaceAll("");
        t = WHITESPACE.matcher(t).replaceAll(" ").trim();

        if (t.length() <= maxLen) return t;

        int cut = t.lastIndexOf(' ', maxLen);
        return (cut > maxLen / 2 ? t.substring(0, cut) : t.substring(0, maxLen)) + "…";
    }
}
