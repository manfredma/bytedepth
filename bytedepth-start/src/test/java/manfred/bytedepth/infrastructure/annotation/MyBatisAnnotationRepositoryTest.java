package manfred.bytedepth.infrastructure.annotation;

import manfred.bytedepth.domain.annotation.PostAnnotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisAnnotationRepositoryTest {

    @Mock
    private PostAnnotationMapper mapper;

    private MyBatisAnnotationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisAnnotationRepository(mapper);
    }

    @Test
    void save_mapsAndInserts() {
        PostAnnotation annotation = new PostAnnotation(null, 1L, 2L, "文本", "批注",
                "yellow", 0, 5, LocalDateTime.of(2026, 8, 10, 12, 0));
        when(mapper.insert(any(PostAnnotationDO.class))).thenAnswer(inv -> {
            PostAnnotationDO data = inv.getArgument(0);
            data.setId(10L);
            return 1;
        });

        PostAnnotation saved = repository.save(annotation);

        assertThat(saved.id()).isEqualTo(10L);
        ArgumentCaptor<PostAnnotationDO> captor = ArgumentCaptor.forClass(PostAnnotationDO.class);
        verify(mapper).insert(captor.capture());
        PostAnnotationDO data = captor.getValue();
        assertThat(data.getPostId()).isEqualTo(1L);
        assertThat(data.getSelectedText()).isEqualTo("文本");
        assertThat(data.getStartOffset()).isEqualTo(0);
    }

    @Test
    void findByPostId_mapsDoListToDomain() {
        PostAnnotationDO data = new PostAnnotationDO();
        data.setId(5L);
        data.setPostId(1L);
        data.setUserId(2L);
        data.setSelectedText("文本");
        data.setAnnotationText("批注");
        data.setColor("green");
        data.setStartOffset(3);
        data.setEndOffset(6);
        data.setCreatedAt(LocalDateTime.of(2026, 8, 10, 12, 0));
        when(mapper.findByPostId(1L)).thenReturn(List.of(data));

        List<PostAnnotation> result = repository.findByPostId(1L);

        assertThat(result).hasSize(1);
        PostAnnotation ann = result.get(0);
        assertThat(ann.id()).isEqualTo(5L);
        assertThat(ann.selectedText()).isEqualTo("文本");
        assertThat(ann.color()).isEqualTo("green");
    }

    @Test
    void findById_existing_returnsDomain() {
        PostAnnotationDO data = new PostAnnotationDO();
        data.setId(5L);
        data.setPostId(1L);
        data.setUserId(2L);
        data.setSelectedText("文本");
        data.setAnnotationText("批注");
        data.setColor("blue");
        data.setStartOffset(0);
        data.setEndOffset(5);
        data.setCreatedAt(LocalDateTime.of(2026, 8, 10, 12, 0));
        when(mapper.selectById(5L)).thenReturn(data);

        Optional<PostAnnotation> result = repository.findById(5L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(5L);
        assertThat(result.get().startOffset()).isEqualTo(0);
        assertThat(result.get().endOffset()).isEqualTo(5);
    }

    @Test
    void findById_missing_returnsEmpty() {
        when(mapper.selectById(99L)).thenReturn(null);

        assertThat(repository.findById(99L)).isEmpty();
    }

    @Test
    void delete_delegates() {
        repository.delete(5L);
        verify(mapper).deleteById(5L);
    }
}
