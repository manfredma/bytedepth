package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAnnotationsQryExeTest {

    @Mock
    private AnnotationRepositoryPort annotationRepository;

    private ListAnnotationsQryExe qry;

    @BeforeEach
    void setUp() {
        qry = new ListAnnotationsQryExe(annotationRepository);
    }

    private static PostAnnotation annotation(long id, int start) {
        return new PostAnnotation(id, 1L, 2L, null, "文本", "批注", "yellow", AnnotationVisibility.PUBLIC,
                start, start + 5, LocalDateTime.now(), false);
    }

    @Test
    void execute_sortsByStartOffsetAscending() {
        when(annotationRepository.findVisibleByPostId(1L, 2L, null))
                .thenReturn(List.of(annotation(3L, 30), annotation(1L, 10), annotation(2L, 20)));

        List<PostAnnotation> result = qry.execute(1L, 2L, null);

        assertThat(result).extracting(PostAnnotation::id).containsExactly(1L, 2L, 3L);
        verify(annotationRepository).findVisibleByPostId(1L, 2L, null);
    }

    @Test
    void execute_empty_returnsEmpty() {
        when(annotationRepository.findVisibleByPostId(1L, null, null)).thenReturn(List.of());

        assertThat(qry.execute(1L, null, null)).isEmpty();
    }
}
