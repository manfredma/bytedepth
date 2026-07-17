package manfred.bytedepth.adapter.web.security;

import manfred.bytedepth.adapter.web.portal.CommentController;
import manfred.bytedepth.app.comment.SubmitCommentCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CommentController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@Import(SecurityConfig.class)
class SecurityRoutingTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private UserDetailsService userDetailsService;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private PersistentTokenRepository persistentTokenRepository;
    @MockBean private SubmitCommentCmdExe submitCommentCmdExe;
    @MockBean private PostRepository postRepository;

    @Test
    void anonymousCommentSubmission_redirectsToLoginBeforeReachingController() throws Exception {
        mockMvc.perform(post("/posts/example/comments")
                .param("content", "hello")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(submitCommentCmdExe, postRepository);
    }
}
