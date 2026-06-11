package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")   // user 是 MySQL 保留字，需要反引号
public class UserDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String email;
    private String avatar;
    private String bio;
    private String status;       // PENDING / ACTIVE / BANNED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
