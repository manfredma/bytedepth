package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = FeedController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class,
        properties = "bytedepth.site.url=https://example.test")
class FeedControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostRepository postRepository;

    @MockitoBean
    private VisitRequestFilter visitRequestFilter;

    @Test
    void feedReturnsRssMediaTypeForPublishedPosts() throws Exception {
        Post post = Post.reconstruct(1L, "rss-entry", "RSS entry", "content", PostStatus.PUBLISHED,
                LocalDateTime.of(2026, 8, 1, 8, 0), LocalDateTime.of(2026, 8, 2, 8, 0),
                LocalDateTime.of(2026, 8, 2, 8, 0), null, null, false);
        when(postRepository.findAllPublished()).thenReturn(List.of(post));

        mockMvc.perform(get("/feed.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/rss+xml")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<link>https://example.test/posts/rss-entry</link>")));
    }
}
