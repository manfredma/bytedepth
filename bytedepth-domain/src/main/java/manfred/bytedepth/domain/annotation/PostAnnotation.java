package manfred.bytedepth.domain.annotation;

import java.time.LocalDateTime;

/**
 * 文章批注值对象。
 * startOffset/endOffset 为文章渲染正文 textContent 的字符偏移。
 */
public record PostAnnotation(
        Long id,
        Long postId,
        Long userId,
        String selectedText,
        String annotationText,
        String color,
        int startOffset,
        int endOffset,
        LocalDateTime createdAt
) {}
