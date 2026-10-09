package manfred.bytedepth.app.comment;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CommentDTO {
    private Long id;
    private Long postId;
    private String postSlug;
    private Long authorId;
    private String authorName;
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
