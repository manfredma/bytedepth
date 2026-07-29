package manfred.bytedepth.app.post;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.List;
import java.util.ArrayList;

/** Extracts reader-facing plain text from Markdown for previews and search indexing. */
public final class MarkdownTextExtractor {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    private MarkdownTextExtractor() {
    }

    /** Returns all reader-visible prose, excluding headings and code blocks. */
    public static String plainText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (Node paragraph : paragraphs(markdown)) {
            appendParagraph(paragraph, text);
        }
        return normalize(text.toString());
    }

    /**
     * Produces a readable lead: prefer the first substantive paragraph and cut at a sentence boundary.
     */
    public static String excerpt(String markdown, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        String fallback = "";
        for (Node node : paragraphs(markdown)) {
            String paragraph = paragraphText(node);
            if (paragraph.isEmpty()) {
                continue;
            }
            if (fallback.isEmpty()) {
                fallback = paragraph;
            }
            if (paragraph.codePointCount(0, paragraph.length()) >= 24) {
                return abbreviate(paragraph, maxLength);
            }
        }
        return abbreviate(fallback, maxLength);
    }

    private static String paragraphText(Node paragraph) {
        StringBuilder text = new StringBuilder();
        appendParagraph(paragraph, text);
        return normalize(text.toString());
    }

    private static List<Node> paragraphs(String markdown) {
        List<Node> paragraphs = new ArrayList<>();
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Paragraph node) {
                paragraphs.add(node);
                visitChildren(node);
            }
        });
        return paragraphs;
    }

    private static void appendParagraph(Node paragraph, StringBuilder output) {
        if (!output.isEmpty()) {
            output.append(' ');
        }
        paragraph.accept(new AbstractVisitor() {
            @Override
            public void visit(Text node) {
                output.append(node.getLiteral());
            }

            @Override
            public void visit(SoftLineBreak node) {
                output.append(' ');
            }

            @Override
            public void visit(HardLineBreak node) {
                output.append(' ');
            }
        });
    }

    private static String abbreviate(String text, int maxLength) {
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxLength);
        int sentenceEnd = lastSentenceEnd(text, end);
        if (sentenceEnd >= end / 2) {
            return text.substring(0, sentenceEnd).trim();
        }
        int wordEnd = text.lastIndexOf(' ', end);
        return text.substring(0, wordEnd >= end / 2 ? wordEnd : end).trim() + "…";
    }

    private static int lastSentenceEnd(String text, int end) {
        int result = -1;
        for (int index = 0; index < end; index++) {
            if ("。！？.!?".indexOf(text.charAt(index)) >= 0) {
                result = index + 1;
            }
        }
        return result;
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
