package manfred.bytedepth.domain.post;

import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;
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
}
