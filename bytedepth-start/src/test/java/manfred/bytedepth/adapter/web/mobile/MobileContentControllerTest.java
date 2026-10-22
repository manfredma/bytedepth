package manfred.bytedepth.adapter.web.mobile;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.NoSuchElementException;
import manfred.bytedepth.app.post.document.MobileContentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MobileContentControllerTest {
    final MobileContentService content = mock(MobileContentService.class);
    final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MobileContentController(content))
            .setControllerAdvice(new MobileExceptionHandler())
            .build();

    @Test
    void unavailableStateContainsNoIdentityOrMetadata() throws Exception {
        when(content.state(1L)).thenReturn(new MobileContentService.PostState(1, "1", "UNAVAILABLE", null));
        mvc.perform(get("/api/mobile/v1/posts/1/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.identity").doesNotExist())
                .andExpect(
                        content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("identity"))));
    }

    @Test
    void withdrawalWinsOverConditional304() throws Exception {
        when(content.withContent(eq(1L), any())).thenThrow(new NoSuchElementException());
        mvc.perform(get("/api/mobile/v1/posts/1/content").header("If-None-Match", "\"previous\""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_UNAVAILABLE"));
    }

    @Test
    void conditionalContentChecksRepresentationBeforeReturning304() throws Exception {
        var doc = new manfred.bytedepth.app.post.document.ArticleDocumentService().render("Hello");
        var representation = new MobileContentService.PublicContent(
                1,
                "1",
                1,
                1,
                doc.document().digest(),
                "hash",
                "1",
                "slug",
                "Title",
                "2026-10-01T00:00:00Z",
                null,
                null,
                doc.html(),
                doc.document(),
                java.util.List.of());
        when(content.withContent(eq(1L), any())).thenAnswer(invocation -> {
            java.util.function.Function<MobileContentService.PublicContent, Object> callback =
                    invocation.getArgument(1);
            return callback.apply(representation);
        });
        mvc.perform(get("/api/mobile/v1/posts/1/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"hash\""))
                .andExpect(jsonPath("$.postId").value("1"));
        mvc.perform(get("/api/mobile/v1/posts/1/content").header("If-None-Match", "\"other\" "))
                .andExpect(status().isOk());
        for (var condition : java.util.List.of("\"hash\"", "W/\"hash\"", "*", "\"other\", \"hash\""))
            mvc.perform(get("/api/mobile/v1/posts/1/content").header("If-None-Match", condition))
                    .andExpect(status().isNotModified())
                    .andExpect(content().string(""));
        when(content.state(1L))
                .thenReturn(new MobileContentService.PostState(1, "1", "AVAILABLE", representation.identity()));
        mvc.perform(get("/api/mobile/v1/posts/1/state").header("If-None-Match", "\"hash\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.representationHash").value("hash"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
