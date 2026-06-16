package manfred.bytedepth.app.series;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SeriesPortalPostDTO {
    private Long id;
    private String slug;
    private String title;
    private Integer seriesOrder;
    private String summary;         // content 前 160 字
    private LocalDateTime publishedAt;
}
