package manfred.bytedepth.adapter.web.mobile;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.app.annotation.AnnotationView;
import manfred.bytedepth.domain.annotation.*;
import org.junit.jupiter.api.Test;

class MobileAnnotationResponseMapperTest {
    static AnnotationView view() {
        return new AnnotationView(
                new PostAnnotation(
                        9007199254740993L,
                        9007199254740995L,
                        2L,
                        "SECRET_OWNER",
                        "quote",
                        null,
                        "yellow",
                        AnnotationVisibility.PRIVATE,
                        0,
                        5,
                        LocalDateTime.of(2026, 10, 1, 8, 0),
                        false),
                new AnnotationPlacement(
                        9007199254740993L,
                        7,
                        1,
                        1,
                        PlacementStatus.LEGACY_UNVERIFIED,
                        List.of(),
                        PlacementReason.LEGACY_EVIDENCE_MISSING),
                "digest");
    }

    @Test
    void whitelistUsesStringIdsUtcAndNeverLeaksOwnerHash() {
        var mapper = new MobileAnnotationResponseMapper(new MarkdownRenderer());
        var mapped = mapper.map(view(), 2L);
        assertThat(mapped).isNotNull();
        String json = new tools.jackson.databind.json.JsonMapper().writeValueAsString(mapped);
        assertThat(json)
                .contains(
                        "\"id\":\"9007199254740993\"",
                        "\"postId\":\"9007199254740995\"",
                        "\"annotationId\":\"9007199254740993\"",
                        "2026-10-01T00:00:00Z",
                        "\"ownedByCurrentVisitor\":true")
                .doesNotContain("SECRET_OWNER", "ownerTokenHash", "userId", "startOffset");
    }

    @Test
    void anonymousCannotClaimOwnershipAndTextIsSafeRendered() {
        var mapped = new MobileAnnotationResponseMapper(new MarkdownRenderer()).map(view(), null);
        assertThat(mapped).isNotNull();
        assertThat(new tools.jackson.databind.json.JsonMapper().writeValueAsString(mapped))
                .contains("\"ownedByCurrentVisitor\":false", "\"annotationText\":\"\"");
    }

    @Test
    void nonEmptyTextIsRenderedAndDifferentAccountHasNoOwnership() {
        var old = view();
        var a = old.annotation();
        var other = new manfred.bytedepth.domain.annotation.PostAnnotation(
                a.id(),
                a.postId(),
                2L,
                null,
                a.selectedText(),
                "**note** <script>bad()</script>",
                a.color(),
                a.visibility(),
                0,
                5,
                a.createdAt(),
                false);
        var result = new MobileAnnotationResponseMapper(new MarkdownRenderer())
                .map(new AnnotationView(other, old.placement(), old.targetDigest()), 3L);
        assertThat(result.ownedByCurrentVisitor()).isFalse();
        assertThat(result.annotationHtml())
                .contains("<strong>", "note", "</strong>")
                .doesNotContain("<script>");
    }
}
