package manfred.bytedepth.infrastructure.comment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class CommentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long authorId;     // 注册用户 ID（旧评论可为 null）
    private String authorName; // 用户名快照
    // authorEmail 已移除
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
