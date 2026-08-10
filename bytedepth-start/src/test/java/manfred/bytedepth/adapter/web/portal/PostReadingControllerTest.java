package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.app.analytics.PostViewLogPort;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.app.analytics.ReadingProgressTokenPort;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PostReadingController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(ThymeleafSecurityHandlerConfig.class)
class PostReadingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPostQryExe getPostQryExe;
    @MockitoBean
    private PostViewLogPort postViewLogPort;
    @MockitoBean
    private ReadingProgressTokenPort readingProgressTokenPort;

    @Test
    void recordsCumulativeReadingProgressForTheMatchingPost() throws Exception {
        PostDTO post = new PostDTO();
        post.setId(12L);
        when(getPostQryExe.executeBySlug("java")).thenReturn(post);
        when(readingProgressTokenPort.belongsToPost("visit-token", 12L)).thenReturn(true);

        mockMvc.perform(post("/posts/java/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitToken\":\"visit-token\",\"activeReadSeconds\":86,\"maxScrollDepth\":83,\"completed\":true}"))
                .andExpect(status().isNoContent());

        verify(postViewLogPort).upsertReadingProgress(12L, "visit-token", 86, 83, true);
    }

    @Test
    void rejectsOutOfRangeProgressBeforeWriting() throws Exception {
        mockMvc.perform(post("/posts/java/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitToken\":\"visit-token\",\"activeReadSeconds\":-1,\"maxScrollDepth\":101,\"completed\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postViewLogPort);
    }

    @Test
    void rejectsEveryOtherInvalidProgressFieldBeforeWriting() throws Exception {
        String tooLongToken = "x".repeat(65);
        String[] invalidRequests = {
                "{\"visitToken\":null,\"activeReadSeconds\":1,\"maxScrollDepth\":1,\"completed\":false}",
                "{\"visitToken\":\"" + tooLongToken + "\",\"activeReadSeconds\":1,\"maxScrollDepth\":1,\"completed\":false}",
                "{\"visitToken\":\"visit-token\",\"activeReadSeconds\":86401,\"maxScrollDepth\":1,\"completed\":false}",
                "{\"visitToken\":\"visit-token\",\"activeReadSeconds\":1,\"maxScrollDepth\":-1,\"completed\":false}",
                "{\"visitToken\":\"visit-token\",\"activeReadSeconds\":1,\"maxScrollDepth\":101,\"completed\":false}"
        };

        for (String invalidRequest : invalidRequests) {
            mockMvc.perform(post("/posts/java/reading-progress")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(postViewLogPort, readingProgressTokenPort);
    }

    @Test
    void ignoresUnknownTokenWithoutWritingProgress() throws Exception {
        PostDTO post = new PostDTO();
        post.setId(12L);
        when(getPostQryExe.executeBySlug("java")).thenReturn(post);
        when(readingProgressTokenPort.belongsToPost("forged-token", 12L)).thenReturn(false);

        mockMvc.perform(post("/posts/java/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitToken\":\"forged-token\",\"activeReadSeconds\":1,\"maxScrollDepth\":1,\"completed\":false}"))
                .andExpect(status().isNoContent());

        verifyNoInteractions(postViewLogPort);
    }
}
