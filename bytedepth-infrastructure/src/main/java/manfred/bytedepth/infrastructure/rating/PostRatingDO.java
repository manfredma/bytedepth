package manfred.bytedepth.infrastructure.rating;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("post_rating")
public class PostRatingDO {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private String visitorToken;
    private Integer score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
