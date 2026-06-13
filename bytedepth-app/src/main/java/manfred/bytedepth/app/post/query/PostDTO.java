package manfred.bytedepth.app.post.query;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostDTO {
    private Long id;
    private String slug;
    private Long authorId;
    private String title;
    private String content;
    private String status;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
    private Long categoryId;
    private String categoryName;
    private String categorySlug;
    private List<String> tagSlugs;
    private Long seriesId;
    private String seriesName;
    private String seriesSlug;
}
