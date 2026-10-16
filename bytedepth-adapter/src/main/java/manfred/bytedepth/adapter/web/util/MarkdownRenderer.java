package manfred.bytedepth.adapter.web.util;

import manfred.bytedepth.app.post.MarkdownTextExtractor;
import manfred.bytedepth.app.post.document.ArticleDocumentService;
import org.springframework.stereotype.Component;

@Component
public class MarkdownRenderer {
    private final ArticleDocumentService documents = new ArticleDocumentService();

    public String render(String markdown) {
        return documents.render(markdown).html();
    }

    public int countVisibleCharacters(String markdown) {
        return MarkdownTextExtractor.visibleCharacterCount(markdown);
    }
}
