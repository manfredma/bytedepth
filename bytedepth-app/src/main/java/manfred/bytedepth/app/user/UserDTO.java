package manfred.bytedepth.app.user;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserDTO {
  private Long id;
  private String username;
  private String status;
  private LocalDateTime createdAt;
}
