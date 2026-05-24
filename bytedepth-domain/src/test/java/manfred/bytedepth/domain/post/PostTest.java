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
}
