package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateAnnotationCmdExeTest {
    private final AnnotationRepositoryPort repository = mock(AnnotationRepositoryPort.class);
    private final UpdateAnnotationCmdExe exe = new UpdateAnnotationCmdExe(repository);

    @Test
    void execute_ownerUpdatesCommentAndVisibility() {
        when(repository.findById(1L)).thenReturn(Optional.of(annotation(2L, null)));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        PostAnnotation updated = exe.execute(1L, 1L, 2L, null, "  新评论  ", AnnotationVisibility.PRIVATE);
        assertThat(updated.annotationText()).isEqualTo("新评论");
        assertThat(updated.visibility()).isEqualTo(AnnotationVisibility.PRIVATE);
    }

    @Test
    void execute_anonymousOwnerCanUpdateAndBlankRemovesComment() {
        when(repository.findById(1L)).thenReturn(Optional.of(annotation(null, "hash")));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(exe.execute(1L, 1L, null, "hash", " ", AnnotationVisibility.PRIVATE).annotationText()).isNull();
    }

    @Test
    void execute_otherVisitorIsRejected() {
        when(repository.findById(1L)).thenReturn(Optional.of(annotation(null, "hash")));
        assertThatThrownBy(() -> exe.execute(1L, 1L, null, "other", "评论", AnnotationVisibility.PUBLIC))
                .isInstanceOf(DomainException.class).hasMessageContaining("只能编辑自己的批注");
    }

    @Test
    void execute_nullVisibilityIsRejected() {
        when(repository.findById(1L)).thenReturn(Optional.of(annotation(2L, null)));
        assertThatThrownBy(() -> exe.execute(1L, 1L, 2L, null, "评论", null))
                .isInstanceOf(DomainException.class).hasMessageContaining("可见范围不能为空");
    }

    @Test
    void execute_annotationFromAnotherPostIsRejected() {
        when(repository.findById(1L)).thenReturn(Optional.of(annotation(2L, null)));
        assertThatThrownBy(() -> exe.execute(1L, 9L, 2L, null, "评论", AnnotationVisibility.PUBLIC))
                .isInstanceOf(DomainException.class).hasMessageContaining("不属于当前文章");
    }

    private static PostAnnotation annotation(Long userId, String hash) {
        return new PostAnnotation(1L, 1L, userId, hash, "文本", "旧评论", "yellow", AnnotationVisibility.PUBLIC,
                0, 2, LocalDateTime.now());
    }
}
