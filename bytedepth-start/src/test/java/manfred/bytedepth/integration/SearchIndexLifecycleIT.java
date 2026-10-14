package manfred.bytedepth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import manfred.bytedepth.app.post.command.DeletePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.command.UpdatePostSlugCmdExe;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.app.search.SearchPostsQryExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.PostSearchPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs only in the Host Agent's isolated staging-it database and search index. */
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class SearchIndexLifecycleIT {
    @Autowired
    private PostRepository posts;

    @Autowired
    private PublishPostCmdExe publish;

    @Autowired
    private UpdatePostSlugCmdExe changeSlug;

    @Autowired
    private DeletePostCmdExe delete;

    @Autowired
    private IndexPostCmdExe sync;

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mvc;

    @Autowired
    private SearchPostsQryExe query;

    @Autowired
    private PostSearchPort search;

    @Autowired
    private JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Value("${bytedepth.search.url}")
    private String searchUrl;

    @org.springframework.beans.factory.annotation.Value("${bytedepth.search.api-key}")
    private String searchApiKey;

    @org.springframework.beans.factory.annotation.Value("${bytedepth.search.index:posts}")
    private String searchIndex;

    private java.util.Map<?, ?> settings() {
        return org.springframework.web.client.RestClient.builder()
                .baseUrl(searchUrl)
                .defaultHeader("Authorization", "Bearer " + searchApiKey)
                .build()
                .get()
                .uri("/indexes/{index}/settings", searchIndex)
                .retrieve()
                .body(java.util.Map.class);
    }

    @Test
    void publishingChangingSlugDeletingAndReconcilingUseTheRealIndex() throws Exception {
        String term = "lifecycle" + UUID.randomUUID().toString().replace("-", "");
        Post saved = posts.save(Post.create(term, "search lifecycle", 1L, term));
        long orphanId = 9000000000L;
        try {
            sync.execute(saved.getId());
            assertThat(search.search(term, 1, 10).getHits()).isEmpty();
            publish.execute(saved.getId());
            assertThat(search.search(term, 1, 10).getHits())
                    .extracting(PostSearchDoc::getSlug)
                    .containsExactly(term);
            var originalSettings = settings();
            assertThat(originalSettings).isNotNull();
            changeSlug.execute(saved.getId(), term + "-new");
            assertThat(search.search(term, 1, 10).getHits())
                    .extracting(PostSearchDoc::getSlug)
                    .containsExactly(term + "-new");
            delete.execute(saved.getId());
            assertThat(search.search(term, 1, 10).getHits()).isEmpty();

            // Reproduce legacy residual documents, including a row that no longer exists.
            search.index(PostSearchDoc.builder()
                    .id(saved.getId())
                    .title(term)
                    .slug(term)
                    .build());
            search.index(PostSearchDoc.builder()
                    .id(orphanId)
                    .title(term)
                    .slug("orphan")
                    .build());
            assertThat(search.search(term, 1, 10).getHits()).hasSize(2);
            assertThat(query.execute(term, 1).getHits()).isEmpty();
            mvc.perform(post("/admin/search/reindex").with(csrf())).andExpect(status().is3xxRedirection());
            mvc.perform(post("/admin/search/reindex").with(csrf()).with(user("reader")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/search/reindex")
                            .with(csrf())
                            .with(user("admin")
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                            "admin:dashboard:view"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));
            assertThat(search.search(term, 1, 10).getHits()).isEmpty();
            assertThat(settings()).isEqualTo(originalSettings);
        } finally {
            search.delete(saved.getId());
            search.delete(orphanId);
            jdbc.update("DELETE FROM post WHERE id = ?", saved.getId());
        }
    }
}
