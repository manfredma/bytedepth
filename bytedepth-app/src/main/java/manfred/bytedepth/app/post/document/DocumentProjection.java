package manfred.bytedepth.app.post.document;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import manfred.bytedepth.domain.post.document.ArticleDocument;
import manfred.bytedepth.domain.post.document.DocumentSegment;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;

/** Built in actual rendering order: unused footnotes and generated navigation never enter it. */
final class DocumentProjection {
    private final StringBuilder text = new StringBuilder();
    private final List<DocumentSegment> segments = new ArrayList<>();
    private final Map<Node, String> blocks = new IdentityHashMap<>();
    private Node previousBlock;
    private int position;

    String append(Node node, String literal, String kind) {
        Node block = block(node);
        if (previousBlock != null
                && previousBlock != block
                && !text.isEmpty()
                && text.charAt(text.length() - 1) != '\n') {
            char separator = previousBlock instanceof TableCell
                            && block instanceof TableCell
                            && previousBlock.getParent() == block.getParent()
                    ? '\t'
                    : '\n';
            text.append(separator);
            position++;
        }
        previousBlock = block;
        String blockId = blocks.computeIfAbsent(block, ignored -> "b" + blocks.size());
        String id = "s" + segments.size();
        int end = position + literal.codePointCount(0, literal.length());
        segments.add(new DocumentSegment(id, blockId, kind, literal, position, end));
        text.append(literal);
        position = end;
        return id;
    }

    private Node block(Node node) {
        Node candidate = node;
        for (Node parent = node; parent != null; parent = parent.getParent()) {
            if (parent instanceof TableCell || parent instanceof org.commonmark.ext.footnotes.InlineFootnote)
                return parent;
            if (parent instanceof Paragraph
                    || parent instanceof Heading
                    || parent instanceof FencedCodeBlock
                    || parent instanceof IndentedCodeBlock
                    || parent instanceof HtmlBlock) candidate = parent;
        }
        return candidate;
    }

    ArticleDocument document() {
        return new ArticleDocument(1, digest(), text.toString(), segments);
    }

    private String digest() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(1);
            writeString(out, text.toString());
            out.writeInt(segments.size());
            for (var segment : segments) {
                writeString(out, segment.id());
                writeString(out, segment.blockId());
                writeString(out, segment.kind());
                writeString(out, segment.text());
                out.writeInt(segment.start());
                out.writeInt(segment.end());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint article projection", exception);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
