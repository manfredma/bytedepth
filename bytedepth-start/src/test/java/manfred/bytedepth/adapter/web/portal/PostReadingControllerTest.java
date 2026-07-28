package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.infrastructure.stats.PostViewLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PostReadingController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class PostReadingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetPostQryExe getPostQryExe;
    @MockBean
    private PostViewLogMapper postViewLogMapper;

    @Test
    void recordsCumulativeReadingProgressForTheMatchingPost() throws Exception {
        PostDTO post = new PostDTO();
        post.setId(12L);
        when(getPostQryExe.executeBySlug("java")).thenReturn(post);

        mockMvc.perform(post("/posts/java/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitToken\":\"visit-token\",\"activeReadSeconds\":86,\"maxScrollDepth\":83,\"completed\":true}"))
                .andExpect(status().isNoContent());

        verify(postViewLogMapper).upsertReadingProgress(12L, "visit-token", 86, 83, true);
    }

    @Test
    void rejectsOutOfRangeProgressBeforeWriting() throws Exception {
        mockMvc.perform(post("/posts/java/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitToken\":\"visit-token\",\"activeReadSeconds\":-1,\"maxScrollDepth\":101,\"completed\":false}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postViewLogMapper);
    }
}
