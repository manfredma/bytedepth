package manfred.bytedepth.adapter.web.util;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MarkdownRenderer {

    private static final List<org.commonmark.Extension> EXTENSIONS =
            List.of(TablesExtension.create());

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();

    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .attributeProviderFactory(ctx -> new HeadingIdProvider())
            .build();

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return renderer.render(parser.parse(markdown));
    }

    private static class HeadingIdProvider implements AttributeProvider {
        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
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
