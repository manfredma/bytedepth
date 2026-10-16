package manfred.bytedepth.domain.annotation;

import java.time.LocalDateTime;

/**
 * 文章批注与不可变原始锚点。旧偏移和 deleted 只保留历史诊断依据；定位失败不得删除。
 * migrationBaselineRevision 非空时表示旧数据的迁移基线，不能声称是真实创建版本。
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
        boolean deleted,
        AnnotationAnchor anchor,
        Long migrationBaselineRevision) {
    public PostAnnotation(
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
            boolean deleted) {
        this(
                id,
                postId,
                userId,
                ownerTokenHash,
                selectedText,
                annotationText,
                color,
                visibility,
                startOffset,
                endOffset,
                createdAt,
                deleted,
                null,
                null);
    }
}
