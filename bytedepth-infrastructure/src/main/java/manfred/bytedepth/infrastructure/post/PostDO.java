package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post")
public class PostDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;        // 文章作者 ID
    private String title;
    private String content;
    private String status;
    private Boolean featured;     // 是否首页推荐
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private Long categoryId;
    private Long seriesId;
    private Integer seriesOrder;
}
