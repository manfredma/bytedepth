package manfred.bytedepth.app.series;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SeriesPortalPostDTO {
    private Long id;
    private String slug;
    private String title;
    private Integer seriesOrder;
    private String summary; // content 前 160 字
    private LocalDateTime publishedAt;
}
