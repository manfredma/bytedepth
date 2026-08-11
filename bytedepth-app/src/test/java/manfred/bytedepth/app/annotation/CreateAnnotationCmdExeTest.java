package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAnnotationCmdExeTest {

    @Mock
    private AnnotationRepositoryPort annotationRepository;

    private CreateAnnotationCmdExe exe;

    @BeforeEach
    void setUp() {
        exe = new CreateAnnotationCmdExe(annotationRepository);
    }

    @Test
    void execute_validInput_savesAndReturns() {
        when(annotationRepository.save(any(PostAnnotation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PostAnnotation result = exe.execute(1L, 2L, "被批注文本", "批注内容",
                AnnotationColor.YELLOW, 10, 20);

        assertThat(result.postId()).isEqualTo(1L);
        assertThat(result.userId()).isEqualTo(2L);
        assertThat(result.selectedText()).isEqualTo("被批注文本");
        assertThat(result.annotationText()).isEqualTo("批注内容");
        assertThat(result.color()).isEqualTo("yellow");
        assertThat(result.startOffset()).isEqualTo(10);
        assertThat(result.endOffset()).isEqualTo(20);
        verify(annotationRepository).save(any(PostAnnotation.class));
    }

    @Test
    void execute_nullPostId_throws() {
        assertThatThrownBy(() -> exe.execute(null, 2L, "文本", "批注", "yellow", 0, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("文章与用户不能为空");
    }

    @Test
    void execute_nullUserId_throws() {
        assertThatThrownBy(() -> exe.execute(1L, null, "文本", "批注", "yellow", 0, 1))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void execute_nullSelectedText_throws() {
        assertThatThrownBy(() -> exe.execute(1L, 2L, null, "批注", "yellow", 0, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注文本不能为空");
    }

    @Test
    void execute_blankSelectedText_throws() {
        assertThatThrownBy(() -> exe.execute(1L, 2L, "  ", "批注", "yellow", 0, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注文本不能为空");
    }

    @Test
    void execute_selectedTextTooLong_throws() {
        String tooLong = "a".repeat(CreateAnnotationCmdExe.MAX_SELECTED_TEXT_LENGTH + 1);
        assertThatThrownBy(() -> exe.execute(1L, 2L, tooLong, "批注", "yellow", 0, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注文本不能为空且不超过");
    }

    @Test
    void execute_nullAnnotationText_throws() {
        assertThatThrownBy(() -> exe.execute(1L, 2L, "文本", null, "yellow", 0, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注内容不能为空");
    }

    @Test
    void execute_blankAnnotationText_throws() {
        assertThatThrownBy(() -> exe.execute(1L, 2L, "文本", "", "yellow", 0, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注内容不能为空");
    }

    @Test
    void execute_annotationTooLong_throws() {
        String tooLong = "b".repeat(CreateAnnotationCmdExe.MAX_ANNOTATION_TEXT_LENGTH + 1);
        assertThatThrownBy(() -> exe.execute(1L, 2L, "文本", tooLong, "yellow", 0, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注内容不能为空且不超过");
    }

    @Test
    void execute_nullColor_throws() {
        assertThatThrownBy(() -> exe.execute(1L, 2L, "文本", "批注", null, 0, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("不支持的批注颜色");
    }

    @Test
    void execute_unsupportedColor_throws() {
        assertThatThrownBy(() -> exe.execute(1L, 2L, "文本", "批注", "pink", 0, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("不支持的批注颜色");
    }

    @Test
    void execute_negativeStartOffset_throws() {
        assertThatThrownBy(() -> exe.execute(1L, 2L, "文本", "批注", "yellow", -1, 1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注偏移越界");
    }

    @Test
    void execute_endNotAfterStart_throws() {
        assertThatThrownBy(() -> exe.execute(1L, 2L, "文本", "批注", "yellow", 10, 10))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注偏移越界");
    }

    @Test
    void execute_offsetBeyondMax_throws() {
        assertThatThrownBy(() -> exe.execute(1L, 2L, "文本", "批注", "yellow",
                CreateAnnotationCmdExe.MAX_OFFSET + 1, CreateAnnotationCmdExe.MAX_OFFSET + 2))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("批注偏移越界");
    }
}
