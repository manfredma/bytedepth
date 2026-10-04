package manfred.bytedepth.infrastructure.stats;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("page_view_log")
public class PageViewLogDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String pagePath;
  private Long userId; // 匿名为 null
  private String ip;
  private String userAgent;
  private String referer;
  private String country;
  private String city;
  private LocalDateTime visitedAt;
}
