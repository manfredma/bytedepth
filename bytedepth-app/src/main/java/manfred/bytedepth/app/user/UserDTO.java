package manfred.bytedepth.app.user;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String status;
    private LocalDateTime createdAt;
}
