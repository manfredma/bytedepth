package manfred.bytedepth.adapter.web.mobile;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.app.annotation.*;
import manfred.bytedepth.app.interaction.MobileInteractionService;
import manfred.bytedepth.app.post.document.ArticleDocumentService;
import manfred.bytedepth.domain.post.PostContentRevision;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MobileAnnotationControllerTest {
    final MobileInteractionService service = mock(MobileInteractionService.class);
    final ListOwnAnnotationsQryExe own = mock(ListOwnAnnotationsQryExe.class);
    final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MobileAnnotationController(
                            service, own, new MobileAnnotationResponseMapper(new MarkdownRenderer())))
            .setCustomArgumentResolvers(
                    new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new MobileExceptionHandler())
            .build();

    @Test
    void publicEnvelopeUsesOneCurrentTarget() throws Exception {
        var doc = new ArticleDocumentService().render("quote").document();
        var target = new PostContentRevision(1, 7, "quote", doc, true);
        when(service.annotations(1L, null))
                .thenReturn(new MobileInteractionService.AnnotationPage(
                        target, List.of(MobileAnnotationResponseMapperTest.view())));
        mvc.perform(get("/api/mobile/v1/posts/1/annotations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.postId").value("1"))
                .andExpect(jsonPath("$.contentVersion").value(7))
                .andExpect(jsonPath("$.projectionVersion").value(doc.projectionVersion()))
                .andExpect(jsonPath("$.digest").value(doc.digest()))
                .andExpect(jsonPath("$.annotations[0].id").value("9007199254740993"));
    }

    @Test
    void crossAccountOwnershipFailureIsTypedJson() throws Exception {
        doThrow(new AnnotationOwnershipException()).when(service).delete(1L, 2L, null);
        mvc.perform(delete("/api/mobile/v1/posts/1/annotations/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void createConflictKindsRemainStructured() throws Exception {
        String body = "{\"annotationText\":\"note\",\"color\":\"yellow\",\"visibility\":\"PRIVATE\"}";
        for (var kind : AnnotationWriteConflictException.Kind.values()) {
            when(service.create(eq(1L), isNull(), any())).thenThrow(new AnnotationWriteConflictException(kind));
            mvc.perform(post("/api/mobile/v1/posts/1/annotations")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().is(kind == AnnotationWriteConflictException.Kind.DELETED_REQUEST ? 410 : 409))
                    .andExpect(jsonPath("$.code").value(kind.name()));
        }
    }

    @Test
    void createUpdateDeleteAndOwnListUseCurrentAccount() throws Exception {
        var principal = new manfred.bytedepth.adapter.web.security.SiteUserDetails(2L, "reader", "hash", List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(
                        org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                                principal, "hash", List.of()));
        var view = MobileAnnotationResponseMapperTest.view();
        try {
            when(service.create(eq(1L), eq(2L), any())).thenReturn(view);
            mvc.perform(post("/api/mobile/v1/posts/1/annotations")
                            .contentType("application/json")
                            .content("{\"annotationText\":\"note\",\"color\":\"yellow\",\"visibility\":\"PRIVATE\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.ownedByCurrentVisitor").value(true));
            when(service.update(1L, 3L, 2L, "new", manfred.bytedepth.domain.annotation.AnnotationVisibility.PUBLIC))
                    .thenReturn(view);
            mvc.perform(
                            patch("/api/mobile/v1/posts/1/annotations/3")
                                    .contentType("application/json")
                                    .content(
                                            "{\"annotationText\":\"new\",\"visibility\":\"PUBLIC\",\"color\":\"forged\",\"anchor\":{}}"))
                    .andExpect(status().isOk());
            verify(service).update(1L, 3L, 2L, "new", manfred.bytedepth.domain.annotation.AnnotationVisibility.PUBLIC);
            mvc.perform(delete("/api/mobile/v1/posts/1/annotations/3")).andExpect(status().isNoContent());
            verify(service).delete(1L, 3L, 2L);
            when(own.execute(2L, 1, 10)).thenReturn(new ListOwnAnnotationsQryExe.PageResult(List.of(view), 1));
            mvc.perform(get("/api/mobile/v1/me/annotations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value("9007199254740993"))
                    .andExpect(jsonPath("$.total").value(1));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void invalidAnchorUses422() throws Exception {
        when(service.create(eq(1L), isNull(), any()))
                .thenThrow(
                        new InvalidAnchorException(manfred.bytedepth.domain.annotation.PlacementReason.MODEL_MISMATCH));
        mvc.perform(post("/api/mobile/v1/posts/1/annotations")
                        .contentType("application/json")
                        .content("{\"annotationText\":\"note\",\"color\":\"yellow\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_ANCHOR"));
    }
}
