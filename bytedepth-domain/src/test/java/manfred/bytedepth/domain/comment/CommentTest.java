package manfred.bytedepth.domain.comment;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CommentTest {

    @Test
    void create_withAuthorId_setsAuthorIdAndApproved() {
        Comment c = Comment.create(1L, 42L, "alice", "Great post!");
        assertEquals(1L, c.getPostId());
        assertEquals(42L, c.getAuthorId());
        assertEquals("alice", c.getAuthorName());
        assertEquals("Great post!", c.getContent());
        assertEquals(CommentStatus.APPROVED, c.getStatus());
        assertNotNull(c.getCreatedAt());
        assertNull(c.getId());
    }

    @Test
    void create_withNullAuthorId_legacyMode_isAllowed() {
        // 旧评论迁移兼容：authorId 可为 null，但 status 仍 APPROVED
        Comment c = Comment.create(1L, null, "Anonymous", "Old comment");
        assertNull(c.getAuthorId());
        assertEquals("Anonymous", c.getAuthorName());
        assertEquals(CommentStatus.APPROVED, c.getStatus());
    }

    @Test
    void reconstruct_shouldRestoreAllFields() {
        LocalDateTime now = LocalDateTime.now();
        Comment c = Comment.reconstruct(10L, 2L, 99L, "testuser",
                "重建的评论", CommentStatus.APPROVED, now);
        assertEquals(10L, c.getId());
        assertEquals(2L, c.getPostId());
        assertEquals(99L, c.getAuthorId());
        assertEquals("testuser", c.getAuthorName());
        assertEquals("重建的评论", c.getContent());
        assertEquals(CommentStatus.APPROVED, c.getStatus());
        assertEquals(now, c.getCreatedAt());
    }
}
