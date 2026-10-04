package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("role")
public class RoleDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;
  private String description;
  private LocalDateTime createdAt;
}
