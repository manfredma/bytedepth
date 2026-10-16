package manfred.bytedepth.app.post.document;

import java.util.Map;
import java.util.Set;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlWriter;

final class ProjectionNodeRenderer implements NodeRenderer {
    private final HtmlWriter html;
    private final DocumentProjection projection;

    ProjectionNodeRenderer(HtmlNodeRendererContext context, DocumentProjection projection) {
        this.html = context.getWriter();
        this.projection = projection;
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(
                Text.class,
                Code.class,
                SoftLineBreak.class,
                HardLineBreak.class,
                HtmlInline.class,
                HtmlBlock.class,
                FencedCodeBlock.class,
                IndentedCodeBlock.class);
    }

    @Override
    public void render(Node node) {
        if (node instanceof Text text) {
            leaf(node, text.getLiteral(), "span", "prose");
        } else if (node instanceof Code code) {
            leaf(node, code.getLiteral(), "code", "prose");
        } else if (node instanceof SoftLineBreak) {
            leaf(node, "\n", "span", "prose");
        } else if (node instanceof HardLineBreak) {
            html.tag("br", Map.of(), true);
            leaf(node, "\n", "span", "prose");
        } else if (node instanceof HtmlInline inline) {
            leaf(node, inline.getLiteral(), "span", "prose");
        } else if (node instanceof HtmlBlock block) {
            leaf(node, block.getLiteral(), "span", "prose");
        } else if (node instanceof FencedCodeBlock code) {
            html.tag("pre");
            String info = code.getInfo().split("[ \t]", 2)[0];
            String id = projection.append(node, code.getLiteral(), info.equals("mermaid") ? "mermaid" : "code");
            html.tag(
                    "code",
                    new java.util.TreeMap<>(
                            info.matches("[A-Za-z0-9_-]{1,64}")
                                    ? Map.of("data-annotation-segment", id, "class", "language-" + info)
                                    : Map.of("data-annotation-segment", id)));
            html.text(code.getLiteral());
            html.tag("/code");
            html.tag("/pre");
            html.line();
        } else {
            IndentedCodeBlock code = (IndentedCodeBlock) node;
            html.tag("pre");
            leaf(node, code.getLiteral(), "code", "code");
            html.tag("/pre");
            html.line();
        }
    }

    private void leaf(Node node, String literal, String tag, String kind) {
        html.tag(tag, Map.of("data-annotation-segment", projection.append(node, literal, kind)));
        html.text(literal);
        html.tag("/" + tag);
    }
}
