package manfred.bytedepth.app.post;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.ext.footnotes.InlineFootnote;
import org.commonmark.node.Node;
import org.commonmark.renderer.html.AttributeProvider;

/** Encodes named labels separately from reference suffixes, avoiding upstream n/n-2 ID collisions. */
public final class FootnoteIdProvider implements AttributeProvider {

    @Override
    public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
        if ((node instanceof FootnoteReference || node instanceof InlineFootnote)
                && tagName.equals("a")
                && attributes.containsKey("id")) {
            String definitionId = attributes.get("href").substring(1);
            attributes.put("id", referenceId(definitionId, attributes.get("id")));
            attributes.put("href", "#" + definitionId(definitionId));
            return;
        }
        if (node instanceof FootnoteDefinition || node instanceof InlineFootnote) {
            if (tagName.equals("li")) {
                attributes.put("id", definitionId(attributes.get("id")));
            } else if (tagName.equals("a")) {
                String originalReference = attributes.get("href").substring(1);
                String definitionId = node instanceof FootnoteDefinition definition
                        ? "fn-" + definition.getLabel()
                        : originalReference.replaceFirst("^fnref", "fn").split("-")[0];
                attributes.put("href", "#" + referenceId(definitionId, originalReference));
            }
        }
    }

    private static String definitionId(String original) {
        return "bd-fn-" + key(original);
    }

    private static String referenceId(String definitionId, String originalReference) {
        String suffix = originalReference.substring(("fnref" + definitionId.substring(2)).length());
        return "bd-fnref-" + key(definitionId) + suffix;
    }

    private static String key(String original) {
        // Named labels are UTF-8 hex with no separator; inline numeric ids use a distinct prefix.
        return original.startsWith("fn-")
                ? "l" + HexFormat.of().formatHex(original.substring(3).getBytes(StandardCharsets.UTF_8))
                : "i" + original.substring(2);
    }
}
