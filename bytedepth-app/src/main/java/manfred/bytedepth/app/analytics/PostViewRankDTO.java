package manfred.bytedepth.app.analytics;

import lombok.Data;

@Data
public class PostViewRankDTO {
    private Long postId;
    private String postTitle;
    private long viewCount;
    private double percent;
}
