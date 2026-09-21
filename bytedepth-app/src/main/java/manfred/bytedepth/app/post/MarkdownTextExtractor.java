package manfred.bytedepth.app.post;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.ArrayList;

/** Extracts reader-facing plain text from Markdown for previews and search indexing. */
public final class MarkdownTextExtractor {

    private static final int CHARACTERS_PER_READING_MINUTE = 500;

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create()))
            .escapeHtml(true)
            .sanitizeUrls(true)
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
     * Returns the textContent produced by the reader's Markdown HTML rendering.
     * Annotation offsets use this representation rather than raw Markdown offsets.
     */
    public static String renderedText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        String html = HTML_RENDERER.render(PARSER.parse(markdown));
        StringBuilder encodedText = new StringBuilder();
        boolean inTag = false;
        for (int index = 0; index < html.length(); index++) {
            char current = html.charAt(index);
            if (!inTag && current == '<') {
                inTag = true;
            } else if (inTag && current == '>') {
                inTag = false;
            } else if (!inTag) {
                encodedText.append(current);
            }
        }

        return decodeHtmlText(new StringReader("<pre>" + encodedText + "</pre>"));
    }

    static String decodeHtmlText(Reader reader) {
        StringBuilder text = new StringBuilder();
        try {
            new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback() {
                    @Override
                    public void handleText(char[] data, int pos) {
                        text.append(data);
                    }
                }, true);
            return text.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to extract rendered Markdown text", exception);
        }
    }

    /** Counts reader-visible Markdown characters using the same AST semantics as the renderer. */
    public static int visibleCharacterCount(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }

        StringBuilder text = new StringBuilder();
        PARSER.parse(markdown).accept(new AbstractVisitor() {
            @Override
            public void visit(Text node) {
                text.append(node.getLiteral());
            }

            @Override
            public void visit(org.commonmark.node.Code node) {
                text.append(node.getLiteral());
            }
        });
        return Math.toIntExact(text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count());
    }

    /** Estimates reader time at 500 visible characters per minute, with a one-minute minimum. */
    public static int estimatedReadingMinutes(String markdown) {
        int visibleCharacters = visibleCharacterCount(markdown);
        return Math.max(1, (visibleCharacters + CHARACTERS_PER_READING_MINUTE - 1)
                / CHARACTERS_PER_READING_MINUTE);
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
