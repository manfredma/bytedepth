package manfred.bytedepth.infrastructure.series;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SeriesPostItemDO {
  private Long id;
  private String slug;
  private String title;
  private Integer seriesOrder;
  private String content;
  private String status;
  private LocalDateTime publishedAt;
}
