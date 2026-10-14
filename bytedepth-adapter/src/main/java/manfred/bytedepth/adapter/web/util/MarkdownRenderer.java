package manfred.bytedepth.adapter.web.util;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import manfred.bytedepth.app.post.FootnoteIdProvider;
import manfred.bytedepth.app.post.MarkdownHtmlPolicy;
import manfred.bytedepth.app.post.MarkdownTextExtractor;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

@Component
public class MarkdownRenderer {

    private static final List<org.commonmark.Extension> EXTENSIONS = MarkdownTextExtractor.EXTENSIONS;

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();

    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            // 文章正文使用 th:utext 输出；原始 HTML 必须转义，避免存储型 XSS。
            .escapeHtml(true)
            .sanitizeUrls(true)
            .nodeRendererFactory(new EnhancedCodeBlockRenderer())
            .attributeProviderFactory(ctx -> new HeadingIdProvider())
            .attributeProviderFactory(ctx -> new FootnoteIdProvider())
            .build();

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return MarkdownHtmlPolicy.sanitize(renderer.render(parser.parse(markdown)));
    }

    /** 统计读者能看到的字符数：忽略 Markdown 标记与空白，并按 Unicode 码点计数。 */
    public int countVisibleCharacters(String markdown) {
        return MarkdownTextExtractor.visibleCharacterCount(markdown);
    }

    private static class HeadingIdProvider implements AttributeProvider {
        private static final Pattern IMAGE_WIDTH_TITLE =
                Pattern.compile("^width=((?:[1-9]\\d{2}|1\\d{3}|2[0-4]\\d{2}))$");

        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof Image image) {
                String imageTitle = image.getTitle();
                var matcher = imageTitle == null ? null : IMAGE_WIDTH_TITLE.matcher(imageTitle);
                if (matcher != null && matcher.matches()) {
                    attributes.put("width", matcher.group(1));
                }
                attributes.remove("title");
            }
            if (node instanceof Heading) {
                String text = extractText(node);
                attributes.put("id", text);
            }
        }

        private String extractText(Node node) {
            StringBuilder sb = new StringBuilder();
            node.accept(new AbstractVisitor() {
                @Override
                public void visit(Text text) {
                    sb.append(text.getLiteral());
                }
            });
            return sb.toString().trim();
        }
    }
}
