package manfred.bytedepth.domain.post;

import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class PostTest {

    @Test
    void create_shouldSetDraftStatus() {
        Post post = Post.create("标题", "内容");
        assertEquals(PostStatus.DRAFT, post.getStatus());
        assertNotNull(post.getCreatedAt());
        assertNull(post.getPublishedAt());
        assertNull(post.getId());
    }

    @Test
    void publish_shouldChangeStatusAndSetPublishedAt() {
        Post post = Post.create("标题", "内容");
        post.publish();
        assertEquals(PostStatus.PUBLISHED, post.getStatus());
        assertNotNull(post.getPublishedAt());
    }

    @Test
    void publish_shouldThrow_whenAlreadyPublished() {
        Post post = Post.create("标题", "内容");
        post.publish();
        assertThrows(DomainException.class, post::publish);
    }

    @Test
    void delete_shouldChangeStatusToDeleted() {
        Post post = Post.create("标题", "内容");
        post.delete();
        assertEquals(PostStatus.DELETED, post.getStatus());
    }

    @Test
    void create_withAuthorId_setsAuthorIdAndFeaturedFalse() {
        Post post = Post.create("Title", "Content", 42L);
        assertEquals(42L, post.getAuthorId());
        assertFalse(post.getFeatured());
    }

    @Test
    void isOwnedBy_sameId_returnsTrue() {
        Post post = Post.create("T", "C", 5L);
        assertTrue(post.isOwnedBy(5L));
    }

    @Test
    void isOwnedBy_differentId_returnsFalse() {
        Post post = Post.create("T", "C", 5L);
        assertFalse(post.isOwnedBy(9L));
    }

    @Test
    void feature_setsFeatureTrue() {
        Post post = Post.create("T", "C", 1L);
        post.feature();
        assertTrue(post.getFeatured());
    }

    @Test
    void unfeature_setsFeaturedFalse() {
        Post post = Post.create("T", "C", 1L);
        post.feature();
        post.unfeature();
        assertFalse(post.getFeatured());
    }

    @Test
    void createAndReconstructOverloads_preserveEveryPersistedField() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 4, 12, 0);
        Post legacy = Post.create("Title", "Content");
        Post withSlug = Post.create("Title", "Content", 7L, "a-post");
        Post base = Post.reconstruct(1L, "Title", "Content", PostStatus.PUBLISHED, time, time, time);
        Post category = Post.reconstruct(2L, "Title", "Content", PostStatus.PUBLISHED, time, time, time, 3L);
        Post complete = Post.reconstruct(3L, "Title", "Content", PostStatus.PUBLISHED, time, time, time, 4L, 5L, null);
        Post persisted = Post.reconstruct(4L, "slug", "Title", "Content", PostStatus.PUBLISHED,
                time, time, time, 6L, 7L, true);

        assertNull(legacy.getAuthorId());
        assertEquals("a-post", withSlug.getSlug());
        assertEquals(1L, base.getId());
        assertEquals(3L, category.getCategoryId());
        assertFalse(complete.getFeatured());
        assertEquals("slug", persisted.getSlug());
        assertEquals(7L, persisted.getAuthorId());
        assertTrue(persisted.getFeatured());
    }

    @Test
    void contentAndRelations_canBeUpdatedAndNullOwnerIsNeverOwned() {
        Post post = Post.create("before", "before");
        post.updateContent("after", "body");
        post.assignCategory(2L);
        post.assignSeries(3L, 4);

        assertEquals("after", post.getTitle());
        assertEquals("body", post.getContent());
        assertNotNull(post.getUpdatedAt());
        assertEquals(2L, post.getCategoryId());
        assertEquals(3L, post.getSeriesId());
        assertEquals(4, post.getSeriesOrder());
        assertFalse(post.isOwnedBy(1L));
    }
}
