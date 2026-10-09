package manfred.bytedepth.infrastructure.reading;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReadingHistoryDO {
    private Long userId;
    private Long postId;
    private Long readCount;
    private Long totalActiveSeconds;
    private LocalDateTime firstReadAt;
    private LocalDateTime lastReadAt;
    private LocalDateTime updatedAt;
    private String postSlug;
    private String title;
}
