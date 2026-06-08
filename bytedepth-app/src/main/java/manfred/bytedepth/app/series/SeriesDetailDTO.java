package manfred.bytedepth.app.series;

import lombok.Data;
import java.util.List;

@Data
public class SeriesDetailDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private List<SeriesDetailPostDTO> posts;
}
