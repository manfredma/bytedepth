package manfred.bytedepth.domain.comment;

import lombok.Getter;
import manfred.bytedepth.domain.common.DomainException;

import java.time.LocalDateTime;

@Getter
public class Comment {

    private Long id;
    private Long postId;
    private String authorName;
    private String authorEmail;
    private String content;
    private CommentStatus status;
    private LocalDateTime createdAt;

    private Comment() {}

    public static Comment create(Long postId, String authorName, String authorEmail, String content) {
        Comment c = new Comment();
        c.postId = postId;
        c.authorName = authorName;
        c.authorEmail = authorEmail;
        c.content = content;
        c.status = CommentStatus.PENDING;
        c.createdAt = LocalDateTime.now();
        return c;
    }

    public static Comment reconstruct(Long id, Long postId, String authorName, String authorEmail,
                                      String content, CommentStatus status, LocalDateTime createdAt) {
        Comment c = new Comment();
        c.id = id;
        c.postId = postId;
        c.authorName = authorName;
        c.authorEmail = authorEmail;
        c.content = content;
        c.status = status;
        c.createdAt = createdAt;
        return c;
    }

    public void approve() {
        if (this.status != CommentStatus.PENDING) {
            throw new DomainException("只有待审核评论可以通过，当前状态：" + this.status);
        }
        this.status = CommentStatus.APPROVED;
    }

    public void reject() {
        this.status = CommentStatus.REJECTED;
    }
}
