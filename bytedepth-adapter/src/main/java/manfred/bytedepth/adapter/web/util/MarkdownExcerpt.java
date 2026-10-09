package manfred.bytedepth.adapter.web.util;

import manfred.bytedepth.app.post.MarkdownTextExtractor;
import org.springframework.stereotype.Component;

/** Exposes Markdown excerpts to Thymeleaf without allowing static-class access in templates. */
@Component("markdownExcerpt")
public class MarkdownExcerpt {

    public String excerpt(String markdown, int maxLength) {
        return MarkdownTextExtractor.excerpt(markdown, maxLength);
    }
}
