package manfred.bytedepth.adapter.web.mobile;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.post.query.*;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MobilePostControllerTest {
    final ListPostsQryExe posts = mock(ListPostsQryExe.class);
    final PublicPostAccess access = mock(PublicPostAccess.class);
    final ListCategoriesQryExe categories = mock(ListCategoriesQryExe.class);
    final ListTagsQryExe tags = mock(ListTagsQryExe.class);
    final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MobilePostController(posts, access, categories, tags))
            .setControllerAdvice(new MobileExceptionHandler())
            .build();

    @Test
    void postsExposePlainSummaryStringIdsUtcAndPageMetadata() throws Exception {
        var p = new PostDTO();
        p.setId(9007199254740993L);
        p.setSlug("slug");
        p.setTitle("Title");
        p.setContent("**hello**");
        p.setStatus("PUBLISHED");
        p.setPublishedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        when(posts.execute(1, 10)).thenReturn(List.of(p));
        when(posts.countPublished()).thenReturn(12L);
        mvc.perform(get("/api/mobile/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].summary").value("hello"))
                .andExpect(jsonPath("$.items[0].publishedAt").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void invalidAndCombinedFiltersReturn400() throws Exception {
        for (var path : List.of("?page=0", "?pageSize=0", "?pageSize=51", "?tag=t&category=c")) {
            mvc.perform(get("/api/mobile/v1/posts" + path)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void resolveUsesOnlyPublicAccess() throws Exception {
        when(access.resolve("current")).thenReturn(new PublicPostAccess.ResolvedPost("7", "current"));
        mvc.perform(get("/api/mobile/v1/posts/resolve?slug=current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value("7"));
    }

    @Test
    void filtersAndDiscoveryUseExistingQueriesAndTaxonomy() throws Exception {
        var p = new PostDTO();
        p.setId(1L);
        p.setTitle("Title");
        p.setContent("Hello");
        p.setCategoryId(8L);
        p.setCategoryName("Java");
        p.setCategorySlug("java");
        when(posts.executeByTag("tag", 2, 1)).thenReturn(List.of(p));
        when(posts.countByTag("tag")).thenReturn(2L);
        mvc.perform(get("/api/mobile/v1/posts?tag=tag&page=2&pageSize=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items[0].category.id").value("8"));
        when(posts.executeByCategory("java", 1, 10)).thenReturn(List.of(p));
        when(posts.countByCategory("java")).thenReturn(10L);
        mvc.perform(get("/api/mobile/v1/posts?category=java").param("tag", " ")).andExpect(status().isOk());
        when(posts.execute(1, 10)).thenReturn(List.of(p));
        when(posts.executeFeatured()).thenReturn(List.of(p));
        var root = new manfred.bytedepth.app.category.CategoryDTO();
        root.setId(8L);
        root.setName("Java");
        root.setSlug("java");
        var child = new manfred.bytedepth.app.category.CategoryDTO();
        child.setId(9L);
        child.setParentId(8L);
        child.setDepth(1);
        when(categories.execute()).thenReturn(List.of(root, child));
        var tag = new manfred.bytedepth.app.tag.TagDTO();
        tag.setId(4L);
        tag.setName("Tag");
        tag.setSlug("tag");
        when(tags.execute()).thenReturn(List.of(tag));
        mvc.perform(get("/api/mobile/v1/discovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured[0].id").value("1"))
                .andExpect(jsonPath("$.categories[1].parentId").value("8"))
                .andExpect(jsonPath("$.tags[0].id").value("4"));
        mvc.perform(get("/api/mobile/v1/posts").param("tag", " ").param("category", " "))
                .andExpect(status().isOk());
        mvc.perform(get("/api/mobile/v1/posts/resolve").param("slug", " ")).andExpect(status().isBadRequest());
    }

    @Test
    void backendOffsetOverflowIsRejectedBeforeQuerying() throws Exception {
        mvc.perform(get("/api/mobile/v1/posts?page=2147483647")).andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThatCode(() -> MobilePage.validate(214748365, 10))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> MobilePage.validate(214748366, 10))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatCode(() -> MobilePage.validate(42949673, 50))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> MobilePage.validate(42949674, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
