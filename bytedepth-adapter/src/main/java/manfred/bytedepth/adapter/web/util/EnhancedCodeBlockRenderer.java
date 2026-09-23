package manfred.bytedepth.adapter.web.util;

import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlNodeRendererFactory;
import org.commonmark.renderer.html.HtmlWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class EnhancedCodeBlockRenderer implements HtmlNodeRendererFactory {

    @Override
    public NodeRenderer create(HtmlNodeRendererContext context) {
        return new Renderer(context);
    }

    private static final class Renderer implements NodeRenderer {
        private static final Pattern SAFE_LANGUAGE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

        private final HtmlNodeRendererContext context;
        private final HtmlWriter html;

        private Renderer(HtmlNodeRendererContext context) {
            this.context = context;
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Document.class);
        }

        @Override
        public void render(Node node) {
            Document document = (Document) node;
            Node current = document.getFirstChild();
            while (current != null) {
                Node next = current.getNext();
                if (current instanceof FencedCodeBlock codeBlock) {
                    CodeBlockMetadata metadata = CodeBlockMetadata.parse(codeBlock.getInfo());
                    if (metadata.tabGroup().isPresent()) {
                        List<FencedCodeBlock> group = collectGroup(codeBlock, metadata.tabGroup().get());
                        if (group.size() > 1) {
                            renderTabs(group);
                        } else {
                            renderEnhancedBlock(codeBlock, metadata);
                        }
                    } else {
                        renderEnhancedBlock(codeBlock, metadata);
                    }
                    current = groupEnd(codeBlock, metadata);
                    continue;
                }
                context.render(current);
                current = next;
            }
        }

        private List<FencedCodeBlock> collectGroup(FencedCodeBlock first, String groupName) {
            List<FencedCodeBlock> group = new ArrayList<>();
            Node current = first;
            while (current instanceof FencedCodeBlock codeBlock) {
                CodeBlockMetadata metadata = CodeBlockMetadata.parse(codeBlock.getInfo());
                if (!metadata.tabGroup().filter(groupName::equals).isPresent()) {
                    break;
                }
                group.add(codeBlock);
                current = current.getNext();
            }
            return group;
        }

        private Node groupEnd(FencedCodeBlock first, CodeBlockMetadata metadata) {
            if (metadata.tabGroup().isEmpty()) {
                return first.getNext();
            }
            Node current = first;
            while (current instanceof FencedCodeBlock codeBlock) {
                CodeBlockMetadata currentMetadata = CodeBlockMetadata.parse(codeBlock.getInfo());
                if (!currentMetadata.tabGroup().filter(metadata.tabGroup().get()::equals).isPresent()) {
                    break;
                }
                current = current.getNext();
            }
            return current;
        }

        private void renderTabs(List<FencedCodeBlock> group) {
            open("div", Map.of("class", "bd-code-tabs"));
            open("div", Map.of("class", "bd-code-tabs__list", "role", "tablist"));
            for (int index = 0; index < group.size(); index++) {
                FencedCodeBlock codeBlock = group.get(index);
                CodeBlockMetadata metadata = CodeBlockMetadata.parse(codeBlock.getInfo());
                Map<String, String> attributes = new LinkedHashMap<>();
                attributes.put("class", "bd-code-tabs__tab");
                attributes.put("type", "button");
                attributes.put("role", "tab");
                attributes.put("aria-selected", String.valueOf(index == 0));
                open("button", attributes);
                html.text(label(metadata));
                close("button");
            }
            close("div");
            open("div", Map.of("class", "bd-code-tabs__panels"));
            for (int index = 0; index < group.size(); index++) {
                FencedCodeBlock codeBlock = group.get(index);
                CodeBlockMetadata metadata = CodeBlockMetadata.parse(codeBlock.getInfo());
                open("div", Map.of(
                        "class", "bd-code-tabs__panel",
                        "role", "tabpanel",
                        "aria-hidden", String.valueOf(index != 0)));
                renderEnhancedBlock(codeBlock, metadata);
                close("div");
            }
            close("div");
            close("div");
        }

        private void renderEnhancedBlock(FencedCodeBlock codeBlock, CodeBlockMetadata metadata) {
            Map<String, String> blockAttributes = new LinkedHashMap<>();
            blockAttributes.put("class", metadata.fold()
                    ? "bd-code-block bd-code-block--collapsed"
                    : "bd-code-block");
            open("div", blockAttributes);
            open("div", Map.of("class", "bd-code-block__header"));
            open("span", Map.of("class", "bd-code-block__language"));
            html.text(languageLabel(metadata));
            close("span");
            metadata.title().ifPresent(title -> {
                open("span", Map.of("class", "bd-code-block__title"));
                html.text(title);
                close("span");
            });
            open("span", Map.of("class", "bd-code-block__actions"));
            open("button", Map.of(
                    "class", "bd-code-block__toggle",
                    "type", "button",
                    "aria-expanded", String.valueOf(!metadata.fold()),
                    "aria-label", metadata.fold() ? "展开代码" : "收起代码"));
            html.text(metadata.fold() ? "展开" : "收起");
            close("button");
            open("button", Map.of(
                    "class", "bd-code-block__copy",
                    "type", "button",
                    "aria-label", "复制代码"));
            html.text("复制");
            close("button");
            close("span");
            close("div");
            Map<String, String> bodyAttributes = new LinkedHashMap<>();
            bodyAttributes.put("class", "bd-code-block__body");
            if (metadata.fold()) {
                bodyAttributes.put("hidden", "hidden");
            }
            open("div", bodyAttributes);
            renderCode(codeBlock, metadata);
            close("div");
            close("div");
        }

        private void renderCode(FencedCodeBlock codeBlock, CodeBlockMetadata metadata) {
            String literal = codeBlock.getLiteral();
            open("div", Map.of("class", "bd-code-block__code"));
            open("div", Map.of("class", "bd-code-block__lines", "aria-hidden", "true"));
            for (int line = 1; line <= lineCount(literal); line++) {
                open("span", Map.of("class", "bd-code-block__line"));
                html.text(String.valueOf(line));
                close("span");
            }
            close("div");
            html.line();
            html.tag("pre");
            Map<String, String> attributes = new LinkedHashMap<>();
            if (SAFE_LANGUAGE.matcher(metadata.language()).matches()) {
                attributes.put("class", "language-" + metadata.language());
            }
            html.tag("code", attributes);
            html.text(literal);
            html.tag("/code");
            html.tag("/pre");
            html.line();
            close("div");
        }

        private String label(CodeBlockMetadata metadata) {
            return metadata.title().orElseGet(() -> languageLabel(metadata));
        }

        private String languageLabel(CodeBlockMetadata metadata) {
            return metadata.language().isBlank() ? "Code" : metadata.language();
        }

        private int lineCount(String literal) {
            return Math.max(1, (int) literal.lines().count());
        }

        private void open(String tag, Map<String, String> attributes) {
            html.line();
            html.tag(tag, attributes);
        }

        private void close(String tag) {
            html.tag("/" + tag);
            html.line();
        }
    }
}
