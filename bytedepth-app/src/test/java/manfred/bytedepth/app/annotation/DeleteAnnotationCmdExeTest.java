package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.PostAnnotation;
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
        return new PostAnnotation(id, 1L, userId, "文本", "批注", "yellow", 0, 5, LocalDateTime.now());
    }

    @Test
    void execute_ownAnnotation_deletes() {
        when(annotationRepository.findById(10L)).thenReturn(Optional.of(annotation(10L, 2L)));

        exe.execute(10L, 2L);

        verify(annotationRepository).delete(10L);
    }

    @Test
    void execute_notFound_throws() {
        when(annotationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(99L, 2L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注不存在");
        verify(annotationRepository, never()).delete(any());
    }

    @Test
    void execute_otherUsersAnnotation_throws() {
        when(annotationRepository.findById(10L)).thenReturn(Optional.of(annotation(10L, 2L)));

        assertThatThrownBy(() -> exe.execute(10L, 99L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("只能删除自己的批注");
        verify(annotationRepository, never()).delete(10L);
    }
}
