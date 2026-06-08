package manfred.bytedepth.app.series;

import lombok.Data;
import java.util.List;

@Data
public class SeriesPortalDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private List<SeriesPortalPostDTO> posts;
    private long totalPosts;
    private int currentPage;
    private long totalPages;
}
