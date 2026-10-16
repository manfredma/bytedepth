package manfred.bytedepth.app.post.document;

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
public class ArticleDocumentService {
    private final Parser parser =
            Parser.builder().extensions(MarkdownTextExtractor.EXTENSIONS).build();

    public RenderedArticle render(String markdown) {
        var projection = new DocumentProjection();
        if (markdown == null || markdown.isBlank()) return new RenderedArticle(projection.document(), "");
        var renderer = HtmlRenderer.builder()
                .extensions(MarkdownTextExtractor.EXTENSIONS)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .nodeRendererFactory(new EnhancedCodeBlockRenderer(projection))
                .nodeRendererFactory(context -> new ProjectionNodeRenderer(context, projection))
                .attributeProviderFactory(ctx -> new HeadingIdProvider())
                .attributeProviderFactory(ctx -> new FootnoteIdProvider())
                .build();
        String html = MarkdownHtmlPolicy.sanitize(renderer.render(parser.parse(markdown)));
        return new RenderedArticle(projection.document(), html);
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
