package manfred.bytedepth.infrastructure.stats;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_view_log")
public class PostViewLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;        // 匿名为 null
    private String ip;
    private String userAgent;
    private String referer;
    private String country;
    private String city;
    private LocalDateTime visitedAt;
    private String visitToken;
    private Integer activeReadSeconds;
    private Integer maxScrollDepth;
    private LocalDateTime lastActivityAt;
    private LocalDateTime completedAt;
    @TableField(exist = false)
    private String postTitle;
}
