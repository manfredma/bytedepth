package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteAnnotationCmdExeTest {

    @Mock
    private AnnotationRepositoryPort annotationRepository;

    private DeleteAnnotationCmdExe exe;

    @BeforeEach
    void setUp() {
        exe = new DeleteAnnotationCmdExe(annotationRepository);
    }

    private static PostAnnotation annotation(Long id, Long userId) {
        return new PostAnnotation(id, 1L, userId, null, "文本", "批注", "yellow", AnnotationVisibility.PUBLIC, 0, 5, LocalDateTime.now(), false);
    }

    @Test
    void execute_ownAnnotation_deletes() {
        when(annotationRepository.findById(10L)).thenReturn(Optional.of(annotation(10L, 2L)));

        exe.execute(10L, 1L, 2L, null);

        verify(annotationRepository).delete(10L);
    }

    @Test
    void execute_notFound_throws() {
        when(annotationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(99L, 1L, 2L, null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注不存在");
        verify(annotationRepository, never()).delete(any());
    }

    @Test
    void execute_otherUsersAnnotation_throws() {
        when(annotationRepository.findById(10L)).thenReturn(Optional.of(annotation(10L, 2L)));

        assertThatThrownBy(() -> exe.execute(10L, 1L, 99L, null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("只能删除自己的批注");
        verify(annotationRepository, never()).delete(10L);
    }

    @Test
    void execute_anonymousOwner_deletes() {
        PostAnnotation annotation = new PostAnnotation(10L, 1L, null, "hash", "文本", null, "yellow", AnnotationVisibility.PRIVATE, 0, 5, LocalDateTime.now(), false);
        when(annotationRepository.findById(10L)).thenReturn(Optional.of(annotation));
        exe.execute(10L, 1L, null, "hash");
        verify(annotationRepository).delete(10L);
    }

    @Test
    void execute_otherPostIsRejected() {
        when(annotationRepository.findById(10L)).thenReturn(Optional.of(annotation(10L, 2L)));
        assertThatThrownBy(() -> exe.execute(10L, 9L, 2L, null))
                .isInstanceOf(DomainException.class).hasMessageContaining("不属于当前文章");
    }

    @Test
    void execute_userOwnerWithVisitorTokenStillDeletes() {
        when(annotationRepository.findById(10L)).thenReturn(Optional.of(annotation(10L, 2L)));
        exe.execute(10L, 1L, 2L, "other-hash");
        verify(annotationRepository).delete(10L);
    }
}
