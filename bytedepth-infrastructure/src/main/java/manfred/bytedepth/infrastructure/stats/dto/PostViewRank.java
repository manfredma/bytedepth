package manfred.bytedepth.infrastructure.stats.dto;

import lombok.Data;

@Data
public class PostViewRank {
    private Long postId;
    private String postTitle;   // SQL 别名 post_title，camelCase 自动映射
    private long viewCount;     // SQL 别名 view_count
    private double percent;     // SQL 固定为 0.0，由 Controller 回填
}
