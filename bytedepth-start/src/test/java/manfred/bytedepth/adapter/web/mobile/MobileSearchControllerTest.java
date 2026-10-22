package manfred.bytedepth.adapter.web.mobile;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import manfred.bytedepth.app.search.SearchPostsQryExe;
import manfred.bytedepth.domain.search.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MobileSearchControllerTest {
    final SearchPostsQryExe search = mock(SearchPostsQryExe.class);
    final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MobileSearchController(search))
            .setControllerAdvice(new MobileExceptionHandler())
            .build();

    @Test
    void searchTrimsInputAndReturnsTextWithoutIndexMarkup() throws Exception {
        when(search.execute("term", 1))
                .thenReturn(new SearchResult(
                        List.of(PostSearchDoc.builder()
                                .id(3L)
                                .slug("current")
                                .title("<em>term</em>")
                                .content("<script>x</script>Safe &amp; <em>term</em>")
                                .build()),
                        1,
                        1,
                        10));
        mvc.perform(get("/api/mobile/v1/search").param("query", " term "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("term"))
                .andExpect(jsonPath("$.items[0].snippet").value("Safe & term"));
    }

    @Test
    void invalidQueryOrPagingIsRejected() throws Exception {
        for (var query : List.of("", " ", "x".repeat(201))) {
            mvc.perform(get("/api/mobile/v1/search").param("query", query)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/mobile/v1/search?query=x&pageSize=20")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/mobile/v1/search?query=x&page=0")).andExpect(status().isBadRequest());
    }

    @Test
    void staleIndexHitsAreCheckedAgainstCurrentStatusAndSlug() throws Exception {
        var index = mock(manfred.bytedepth.domain.search.PostSearchPort.class);
        var posts = mock(manfred.bytedepth.domain.post.PostRepository.class);
        var date = java.time.LocalDateTime.of(2026, 10, 1, 8, 0);
        when(posts.findById(1L))
                .thenReturn(java.util.Optional.of(manfred.bytedepth.domain.post.Post.reconstruct(
                        1L,
                        "current",
                        "Title",
                        "Body",
                        manfred.bytedepth.domain.post.PostStatus.PUBLISHED,
                        date,
                        date,
                        date,
                        null,
                        null,
                        false,
                        1)));
        when(posts.findById(2L))
                .thenReturn(java.util.Optional.of(manfred.bytedepth.domain.post.Post.create("Draft", "Secret")));
        when(index.search("term", 1, 10))
                .thenReturn(new SearchResult(
                        List.of(
                                PostSearchDoc.builder().id(1L).slug("old").build(),
                                PostSearchDoc.builder()
                                        .id(2L)
                                        .slug("secret")
                                        .title("Secret")
                                        .build()),
                        2,
                        1,
                        10));
        var http = MockMvcBuilders.standaloneSetup(new MobileSearchController(new SearchPostsQryExe(index, posts)))
                .build();
        http.perform(get("/api/mobile/v1/search?query=term"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("current"))
                .andExpect(jsonPath("$.items[0].title").value(""));
    }

    @Test
    void maximumLengthAndEmptyResultAreValid() throws Exception {
        when(search.execute("x".repeat(200), 1)).thenReturn(new SearchResult(List.of(), 0, 1, 10));
        mvc.perform(get("/api/mobile/v1/search").param("query", "x".repeat(200)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void nativePlainTextPreservesLiteralBackslashes() throws Exception {
        when(search.execute("path", 1))
                .thenReturn(new SearchResult(
                        List.of(PostSearchDoc.builder()
                                .id(1L)
                                .title("literal\\[bracket]")
                                .content("Path \\[a] &amp; B")
                                .build()),
                        1,
                        1,
                        10));
        mvc.perform(get("/api/mobile/v1/search?query=path"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("literal\\[bracket]"))
                .andExpect(jsonPath("$.items[0].snippet").value("Path \\[a] & B"));
    }
}
