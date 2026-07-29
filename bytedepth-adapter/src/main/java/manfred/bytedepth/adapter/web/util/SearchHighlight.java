package manfred.bytedepth.adapter.web.util;

import manfred.bytedepth.app.post.MarkdownTextExtractor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Makes MeiliSearch highlighting safe while keeping only its expected {@code <em>} tags. */
@Component("searchHighlight")
public class SearchHighlight {

    private static final String OPEN_EM = "\uE000OPEN_EM\uE001";
    private static final String CLOSE_EM = "\uE000CLOSE_EM\uE001";

    public String title(String highlightedTitle) {
        return safeHtml(highlightedTitle);
    }

    public String snippet(String highlightedMarkdown) {
        String protectedHighlight = protectHighlight(highlightedMarkdown);
        return safeHtml(MarkdownTextExtractor.excerpt(protectedHighlight, 240));
    }

    private static String safeHtml(String text) {
        String escaped = HtmlUtils.htmlEscape(protectHighlight(text == null ? "" : text));
        return escaped.replace(OPEN_EM, "<em>").replace(CLOSE_EM, "</em>");
    }

    private static String protectHighlight(String text) {
        return text.replace("<em>", OPEN_EM).replace("</em>", CLOSE_EM);
    }
}
