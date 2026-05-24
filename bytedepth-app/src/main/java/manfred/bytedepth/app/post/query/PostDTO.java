package manfred.bytedepth.app.post.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostDTO {
    private Long id;
    private String title;
    private String content;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
