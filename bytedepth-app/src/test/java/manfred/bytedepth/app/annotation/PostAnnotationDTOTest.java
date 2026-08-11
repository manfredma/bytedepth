package manfred.bytedepth.app.annotation;

import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PostAnnotationDTOTest {
    @Test
    void from_loggedInOwnerMarksDtoOwned() {
        assertThat(PostAnnotationDTO.from(annotation(2L, null), 2L, null).ownedByCurrentVisitor()).isTrue();
    }

    @Test
    void from_anonymousOwnerMarksDtoOwned() {
        assertThat(PostAnnotationDTO.from(annotation(null, "hash"), null, "hash").ownedByCurrentVisitor()).isTrue();
    }

    @Test
    void from_nonOwnerDoesNotExposeOwnership() {
        PostAnnotationDTO dto = PostAnnotationDTO.from(annotation(2L, null), 3L, "other");
        assertThat(dto.ownedByCurrentVisitor()).isFalse();
        assertThat(dto.visibility()).isEqualTo(AnnotationVisibility.PUBLIC);
        assertThat(dto.selectedText()).isEqualTo("文本");
    }

    private static PostAnnotation annotation(Long userId, String tokenHash) {
        return new PostAnnotation(1L, 9L, userId, tokenHash, "文本", "评论", "yellow", AnnotationVisibility.PUBLIC,
                0, 2, LocalDateTime.of(2026, 8, 11, 18, 0));
    }
}
