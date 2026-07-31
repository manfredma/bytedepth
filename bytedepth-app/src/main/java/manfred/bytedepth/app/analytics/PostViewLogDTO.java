package manfred.bytedepth.app.analytics;

import java.time.LocalDateTime;
import lombok.Data;

/** A view log prepared for administrative presentation. */
@Data
public class PostViewLogDTO {
    private Long id;
    private Long postId;
    private Long userId;
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
    private String postTitle;
}
