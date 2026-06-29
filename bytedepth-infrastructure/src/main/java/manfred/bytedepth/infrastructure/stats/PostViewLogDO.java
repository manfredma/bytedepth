package manfred.bytedepth.infrastructure.stats;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
}
