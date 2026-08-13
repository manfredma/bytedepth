package manfred.bytedepth.domain.annotation;

import java.time.LocalDateTime;

/**
 * 文章批注值对象。
 * startOffset/endOffset 为文章渲染正文 textContent 的字符偏移。
 * deleted 为逻辑删除标记：文章内容变更后批注位置无法重算时标记为已删除。
 */
public record PostAnnotation(
        Long id,
        Long postId,
        Long userId,
        String ownerTokenHash,
        String selectedText,
        String annotationText,
        String color,
        AnnotationVisibility visibility,
        int startOffset,
        int endOffset,
        LocalDateTime createdAt,
        boolean deleted
) {}
