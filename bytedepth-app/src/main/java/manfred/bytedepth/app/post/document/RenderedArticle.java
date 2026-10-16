package manfred.bytedepth.app.post.document;

import manfred.bytedepth.domain.post.document.ArticleDocument;

public record RenderedArticle(ArticleDocument document, String html) {}
