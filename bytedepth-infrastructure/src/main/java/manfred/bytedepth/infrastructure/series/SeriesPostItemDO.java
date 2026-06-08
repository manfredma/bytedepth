package manfred.bytedepth.infrastructure.series;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SeriesPostItemDO {
    private Long id;
    private String title;
    private Integer seriesOrder;
    private String content;
    private String status;
    private LocalDateTime publishedAt;
}
