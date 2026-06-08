package manfred.bytedepth.app.series;

import lombok.Data;

@Data
public class SeriesDetailPostDTO {
    private Long id;
    private String title;
    private Integer seriesOrder;
    private String status;  // PUBLISHED / DRAFT
}
