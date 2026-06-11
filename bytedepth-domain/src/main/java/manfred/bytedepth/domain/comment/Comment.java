package manfred.bytedepth.domain.comment;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Comment {

    private Long id;
    private Long postId;
    private Long authorId;      // 注册用户 ID（旧评论可为 null）
    private String authorName;  // 用户名快照（显示用）
    private String content;
    private CommentStatus status;
    private LocalDateTime createdAt;

    private Comment() {}

    /**
     * 创建评论：直接 APPROVED，移除匿名字段。
     * authorId 可为 null（兼容旧数据迁移场景）。
     */
    public static Comment create(Long postId, Long authorId,
                                 String authorName, String content) {
        Comment c = new Comment();
        c.postId = postId;
        c.authorId = authorId;
        c.authorName = authorName;
        c.content = content;
        c.status = CommentStatus.APPROVED;
        c.createdAt = LocalDateTime.now();
        return c;
    }

    public static Comment reconstruct(Long id, Long postId, Long authorId,
                                      String authorName, String content,
                                      CommentStatus status, LocalDateTime createdAt) {
        Comment c = new Comment();
        c.id = id;
        c.postId = postId;
        c.authorId = authorId;
        c.authorName = authorName;
        c.content = content;
        c.status = status;
        c.createdAt = createdAt;
        return c;
    }
    // approve() 和 reject() 已移除——评论不再走审核流
}
