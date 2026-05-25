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
    private String authorName;
    private String authorEmail;
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
