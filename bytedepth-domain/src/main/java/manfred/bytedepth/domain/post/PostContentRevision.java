package manfred.bytedepth.domain.post;

import manfred.bytedepth.domain.post.document.ArticleDocument;

public record PostContentRevision(
        long postId, long contentVersion, String markdown, ArticleDocument document, boolean baseline) {}
