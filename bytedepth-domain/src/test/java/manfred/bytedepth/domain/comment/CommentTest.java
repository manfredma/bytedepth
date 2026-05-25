package manfred.bytedepth.domain.comment;

import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommentTest {

    @Test
    void create_shouldSetPendingStatus() {
        Comment c = Comment.create(1L, "张三", "zhang@example.com", "很好的文章");
        assertEquals(CommentStatus.PENDING, c.getStatus());
        assertEquals(1L, c.getPostId());
        assertEquals("张三", c.getAuthorName());
        assertEquals("zhang@example.com", c.getAuthorEmail());
        assertEquals("很好的文章", c.getContent());
        assertNotNull(c.getCreatedAt());
        assertNull(c.getId());
    }

    @Test
    void approve_shouldChangeStatusToApproved() {
        Comment c = Comment.create(1L, "李四", "li@example.com", "内容不错");
        c.approve();
        assertEquals(CommentStatus.APPROVED, c.getStatus());
    }

    @Test
    void approve_shouldThrow_whenNotPending() {
        Comment c = Comment.create(1L, "王五", "wang@example.com", "好文");
        c.approve();
        assertThrows(DomainException.class, c::approve);
    }

    @Test
    void reject_shouldChangeStatusToRejected() {
        Comment c = Comment.create(1L, "赵六", "zhao@example.com", "垃圾评论");
        c.reject();
        assertEquals(CommentStatus.REJECTED, c.getStatus());
    }

    @Test
    void reject_canRejectFromAnyStatus() {
        Comment c = Comment.create(2L, "钱七", "qian@example.com", "评论内容");
        c.approve();
        // reject can be called from APPROVED status without throwing
        c.reject();
        assertEquals(CommentStatus.REJECTED, c.getStatus());
    }

    @Test
    void reconstruct_shouldRestoreAllFields() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Comment c = Comment.reconstruct(10L, 2L, "测试用户", "test@example.com",
                "重建的评论", CommentStatus.APPROVED, now);
        assertEquals(10L, c.getId());
        assertEquals(2L, c.getPostId());
        assertEquals("测试用户", c.getAuthorName());
        assertEquals("test@example.com", c.getAuthorEmail());
        assertEquals("重建的评论", c.getContent());
        assertEquals(CommentStatus.APPROVED, c.getStatus());
        assertEquals(now, c.getCreatedAt());
    }
}
