package manfred.bytedepth.app.series;

import lombok.Data;

@Data
public class SeriesPostItemDTO {
    private Long id;
    private String slug;
    private String title;
    private Integer seriesOrder;
}
