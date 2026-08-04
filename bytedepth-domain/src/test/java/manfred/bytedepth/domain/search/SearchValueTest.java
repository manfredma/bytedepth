package manfred.bytedepth.domain.search;

import manfred.bytedepth.domain.post.HotPost;
import manfred.bytedepth.domain.post.Post;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchValueTest {

    @Test
    void searchDocumentBuilderAndGetters_preserveAllFields() {
        var builder = PostSearchDoc.builder().id(1L).slug("slug").title("title").content("body")
                .categoryName("Java").categorySlug("java").tags(List.of("spring")).seriesName("series");
        PostSearchDoc document = builder.build();

        assertEquals(1L, document.getId());
        assertEquals("slug", document.getSlug());
        assertEquals("title", document.getTitle());
        assertEquals("body", document.getContent());
        assertEquals("Java", document.getCategoryName());
        assertEquals("java", document.getCategorySlug());
        assertEquals(List.of("spring"), document.getTags());
        assertEquals("series", document.getSeriesName());
        assertTrue(builder.toString().contains("slug=slug"));
    }

    @Test
    void resultPagingAndRatingValues_coverAllBranches() {
        SearchResult first = new SearchResult(List.of(), 5, 1, 2);
        SearchResult last = new SearchResult(List.of(), 5, 3, 2);
        HotPost hotPost = new HotPost(Post.create("title", "body"), 9);

        assertEquals(3, first.totalPages());
        assertFalse(first.hasPrev());
        assertTrue(first.hasNext());
        assertTrue(last.hasPrev());
        assertFalse(last.hasNext());
        assertEquals(List.of(), first.getHits());
        assertEquals(5, first.getTotalHits());
        assertEquals(1, first.getPage());
        assertEquals(2, first.getSize());
        assertEquals(9, hotPost.viewCount());
        assertEquals("title", hotPost.post().getTitle());
        assertEquals(4.5, new manfred.bytedepth.domain.rating.PostRatingStats(4.5, 2).averageRating());
        assertEquals(2, new manfred.bytedepth.domain.rating.PostRatingStats(4.5, 2).ratingCount());
    }
}
