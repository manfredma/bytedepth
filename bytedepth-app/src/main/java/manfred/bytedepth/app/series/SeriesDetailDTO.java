package manfred.bytedepth.app.series;

import java.util.List;
import lombok.Data;

@Data
public class SeriesDetailDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private List<SeriesDetailPostDTO> posts;
}
