package manfred.bytedepth.app.annotation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;

class CreateAnnotationCmdExeTest {
    @Test
    void U09_invalidMetadataNeverWrites() {
        Object[][] cases = {
            {null, 2L, null, "文本", null, "yellow", AnnotationVisibility.PUBLIC, 0, 2},
            {1L, null, null, "文本", null, "yellow", AnnotationVisibility.PUBLIC, 0, 2},
            {1L, null, " ", "文本", null, "yellow", AnnotationVisibility.PUBLIC, 0, 2},
            {1L, 2L, null, null, null, "yellow", AnnotationVisibility.PUBLIC, 0, 2},
            {1L, 2L, null, " ", null, "yellow", AnnotationVisibility.PUBLIC, 0, 2},
            {1L, 2L, null, "a".repeat(501), null, "yellow", AnnotationVisibility.PUBLIC, 0, 2},
            {1L, 2L, null, "文本", "a".repeat(2001), "yellow", AnnotationVisibility.PUBLIC, 0, 2},
            {1L, 2L, null, "文本", null, null, AnnotationVisibility.PUBLIC, 0, 2},
            {1L, 2L, null, "文本", null, "pink", AnnotationVisibility.PUBLIC, 0, 2},
            {1L, 2L, null, "文本", null, "yellow", null, 0, 2},
            {1L, 2L, null, "文本", null, "yellow", AnnotationVisibility.PUBLIC, -1, 2},
            {1L, 2L, null, "文本", null, "yellow", AnnotationVisibility.PUBLIC, 2, 2},
            {1L, 2L, null, "文本", null, "yellow", AnnotationVisibility.PUBLIC, 0, 1000001}
        };
        for (var c : cases)
            assertThrows(
                    DomainException.class,
                    () -> CreateAnnotationCmdExe.validate(
                            (Long) c[0],
                            (Long) c[1],
                            (String) c[2],
                            (String) c[3],
                            (String) c[4],
                            (String) c[5],
                            (AnnotationVisibility) c[6],
                            (int) c[7],
                            (int) c[8]));
        assertDoesNotThrow(() -> CreateAnnotationCmdExe.validate(
                1L, null, "hash", "😀".repeat(500), null, "yellow", AnnotationVisibility.PRIVATE, 0, 500));
    }

    @Test
    void U38_colorsAnonymousPrivateAndEmptyCommentsStaySupported() {
        for (var color : java.util.List.of("yellow", "green", "blue", "red")) {
            var f = new AnnotationCommandProtocolTest();
            f.save();
            for (String comment : new String[] {null, "  ", "  内容  "}) {
                var result = f.create
                        .execute(
                                1L,
                                null,
                                "a".repeat(64),
                                new CreateAnnotationRequest(f.anchor, comment, color, AnnotationVisibility.PRIVATE))
                        .annotation();
                assertEquals("a".repeat(64), result.ownerTokenHash());
                assertEquals(color, result.color());
                assertEquals(AnnotationVisibility.PRIVATE, result.visibility());
                assertEquals(comment == null || comment.isBlank() ? null : "内容", result.annotationText());
            }
        }
    }

    @Test
    void U12_oldProtocolFailsClosed() {
        var f = new AnnotationCommandProtocolTest();
        assertThrows(
                InvalidAnchorException.class,
                () -> f.create.execute(
                        1L, 2L, null, new CreateAnnotationRequest(null, null, "yellow", AnnotationVisibility.PUBLIC)));
        verify(f.annotations, never()).save(any());
        assertThrows(
                InvalidAnchorException.class, () -> f.create.execute(1L, 2L, null, (CreateAnnotationRequest) null));
    }
}
